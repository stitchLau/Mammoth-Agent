package com.mammoth.artifactspike;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * ADR-0004 spike — immutable artifact storage for Mammoth M1.
 *
 * <p>Compares two strategies over the same synthetic artifact set (including duplicate content):
 * <ul>
 *   <li>A: local content-addressed directory ({@code artifacts/<sha[0..2]>/<sha[2..4]>/<sha>});</li>
 *   <li>B: SQLite BLOB with a UNIQUE sha256 column.</li>
 * </ul>
 *
 * <p>Measures write/read time, deduplication, stored bytes, hash verification, read-only
 * enforcement, backup size/time and delete→restore behavior. Output is UTF-8 JSON written to the
 * path given on the command line. Disposable spike code, never product code.
 */
public final class ArtifactSpike {

  private record Sample(String name, byte[] content) {
  }

  private ArtifactSpike() {
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 3) {
      System.err.println("usage: filesystem <workDir> <outJson> | sqlite <workDir> <outJson>");
      System.exit(2);
    }
    try {
      Class.forName("org.sqlite.JDBC");
    } catch (ClassNotFoundException ignored) {
      // reported by connection attempt
    }
    Path work = Path.of(args[1]).toAbsolutePath();
    Path out = Path.of(args[2]);
    List<Sample> samples = samples();
    Map<String, Object> result = switch (args[0]) {
      case "filesystem" -> runFilesystem(work, samples);
      case "sqlite" -> runSqlite(work, samples);
      default -> throw new IllegalArgumentException("unknown mode: " + args[0]);
    };
    result.put("_out", out.toAbsolutePath().toString());
    String json = toJson(result);
    if (out.getParent() != null) {
      Files.createDirectories(out.getParent());
    }
    Files.writeString(out, json, StandardCharsets.UTF_8);
    System.out.println(json);
  }

  /** 6 artifacts, 3 unique contents (duplicates exercise deduplication). */
  private static List<Sample> samples() {
    List<Sample> list = new ArrayList<>();
    byte[] small = ("Mammoth 项目资料：收货流程异常处理说明。"
        + "现场角色、异常路径、验收标准。").repeat(8).getBytes(StandardCharsets.UTF_8);
    byte[] medium = new byte[100 * 1024];
    new Random(42).nextBytes(medium);
    byte[] large = new byte[5 * 1024 * 1024];
    new Random(7).nextBytes(large);
    list.add(new Sample("doc-small.md", small));
    list.add(new Sample("doc-small-copy.md", small.clone()));
    list.add(new Sample("blob-medium.bin", medium));
    list.add(new Sample("blob-large.bin", large));
    list.add(new Sample("blob-large-copy.bin", large.clone()));
    list.add(new Sample("empty.txt", new byte[0]));
    return list;
  }

  private static Map<String, Object> runFilesystem(Path work, List<Sample> samples) throws Exception {
    Path root = work.resolve("artifacts");
    Path backup = work.resolve("artifacts-backup");
    deleteRecursively(root);
    deleteRecursively(backup);
    Files.createDirectories(root);

    Map<String, Object> r = base("filesystem");
    long logicalBytes = 0;
    long storedBytes = 0;
    int dedupSkipped = 0;
    long writeStart = System.nanoTime();
    for (Sample sample : samples) {
      logicalBytes += sample.content().length;
      String sha = sha256(sample.content());
      Path target = objectPath(root, sha);
      if (Files.exists(target)) {
        dedupSkipped++;
        continue;
      }
      Files.createDirectories(target.getParent());
      Path tmp = target.resolveSibling(sha + ".tmp");
      Files.write(tmp, sample.content());
      Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
      setReadOnly(target);
      storedBytes += Files.size(target);
    }
    r.put("writeMs", (System.nanoTime() - writeStart) / 1_000_000);
    r.put("logicalBytes", logicalBytes);
    r.put("storedBytes", storedBytes);
    r.put("dedupSkipped", dedupSkipped);

    // read + hash verify
    long readStart = System.nanoTime();
    boolean verified = true;
    for (Sample sample : samples) {
      String sha = sha256(sample.content());
      Path target = objectPath(root, sha);
      byte[] read = Files.readAllBytes(target);
      verified &= sha256(read).equals(sha);
    }
    r.put("readMs", (System.nanoTime() - readStart) / 1_000_000);
    r.put("hashVerified", verified);

    // read-only enforcement: an overwrite attempt must fail
    boolean readOnly = true;
    try (var stream = Files.walk(root)) {
      Path any = stream.filter(Files::isRegularFile).findFirst().orElseThrow();
      try {
        Files.write(any, new byte[] {1, 2, 3});
        readOnly = false;
      } catch (IOException expected) {
        readOnly = true;
      }
    }
    r.put("readOnlyEnforced", readOnly);

    // backup (copy tree) + restore (delete one object, copy back)
    long backupStart = System.nanoTime();
    copyTree(root, backup);
    r.put("backupMs", (System.nanoTime() - backupStart) / 1_000_000);
    r.put("backupBytes", treeSize(backup));
    String victimSha = sha256(samples.get(2).content());
    Path victim = objectPath(root, victimSha);
    boolean deleteBlocked = false;
    try {
      Files.delete(victim);
    } catch (IOException expected) {
      deleteBlocked = true;
    }
    r.put("deleteBlockedWhenReadOnly", deleteBlocked);
    Files.setAttribute(victim, "dos:readonly", false);
    Files.delete(victim);
    boolean missingAfterDelete = !Files.exists(victim);
    copyTree(backup, root);
    boolean restored = Files.exists(victim) && sha256(Files.readAllBytes(victim)).equals(victimSha);
    r.put("deleteThenMissing", missingAfterDelete);
    r.put("restoredFromBackup", restored);

    deleteRecursively(backup);
    return r;
  }

  private static Map<String, Object> runSqlite(Path work, List<Sample> samples) throws Exception {
    Files.createDirectories(work);
    Path db = work.resolve("artifacts.db");
    Path backupDb = work.resolve("artifacts-backup.db");
    Files.deleteIfExists(db);
    Files.deleteIfExists(backupDb);
    String url = "jdbc:sqlite:" + db;

    Map<String, Object> r = base("sqlite");
    long logicalBytes = 0;
    int dedupSkipped = 0;
    long writeStart = System.nanoTime();
    try (Connection c = DriverManager.getConnection(url)) {
      execute(c, "PRAGMA journal_mode=WAL");
      execute(c, "CREATE TABLE artifact ("
          + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
          + "sha256 TEXT NOT NULL UNIQUE,"
          + "name TEXT NOT NULL,"
          + "size INTEGER NOT NULL,"
          + "bytes BLOB NOT NULL)");
      try (PreparedStatement ps = c.prepareStatement(
          "INSERT OR IGNORE INTO artifact (sha256,name,size,bytes) VALUES (?,?,?,?)")) {
        for (Sample sample : samples) {
          logicalBytes += sample.content().length;
          ps.setString(1, sha256(sample.content()));
          ps.setString(2, sample.name());
          ps.setLong(3, sample.content().length);
          ps.setBytes(4, sample.content());
          int inserted = ps.executeUpdate();
          if (inserted == 0) {
            dedupSkipped++;
          }
        }
        ps.executeBatch();
      }
    }
    r.put("writeMs", (System.nanoTime() - writeStart) / 1_000_000);
    r.put("logicalBytes", logicalBytes);
    r.put("dedupSkipped", dedupSkipped);

    long readStart = System.nanoTime();
    boolean verified = true;
    try (Connection c = DriverManager.getConnection(url);
         PreparedStatement ps = c.prepareStatement("SELECT bytes FROM artifact WHERE sha256=?")) {
      for (Sample sample : samples) {
        ps.setString(1, sha256(sample.content()));
        try (ResultSet rs = ps.executeQuery()) {
          byte[] bytes = rs.next() ? rs.getBytes(1) : null;
          verified &= bytes != null && sha256(bytes).equals(sha256(sample.content()));
        }
      }
      r.put("rows", countRows(c));
      r.put("dbSizeBytes", Files.size(db));
    }
    r.put("readMs", (System.nanoTime() - readStart) / 1_000_000);
    r.put("hashVerified", verified);

    // backup via VACUUM INTO, then delete/restore round-trip
    long backupStart = System.nanoTime();
    try (Connection c = DriverManager.getConnection(url)) {
      try (PreparedStatement ps = c.prepareStatement("VACUUM INTO ?")) {
        ps.setString(1, backupDb.toAbsolutePath().toString());
        ps.execute();
      }
    }
    r.put("backupMs", (System.nanoTime() - backupStart) / 1_000_000);
    r.put("backupBytes", Files.size(backupDb));

    String victimSha = sha256(samples.get(2).content());
    try (Connection c = DriverManager.getConnection(url)) {
      try (PreparedStatement ps = c.prepareStatement("DELETE FROM artifact WHERE sha256=?")) {
        ps.setString(1, victimSha);
        ps.executeUpdate();
      }
    }
    boolean missingAfterDelete = lookupMissing(url, victimSha);
    Files.copy(backupDb, db, StandardCopyOption.REPLACE_EXISTING);
    boolean restored = !lookupMissing(url, victimSha);
    r.put("deleteThenMissing", missingAfterDelete);
    r.put("restoredFromBackup", restored);
    Files.deleteIfExists(backupDb);
    return r;
  }

  private static boolean lookupMissing(String url, String sha) throws Exception {
    try (Connection c = DriverManager.getConnection(url);
         PreparedStatement ps = c.prepareStatement("SELECT 1 FROM artifact WHERE sha256=?")) {
      ps.setString(1, sha);
      try (ResultSet rs = ps.executeQuery()) {
        return !rs.next();
      }
    }
  }

  private static int countRows(Connection c) throws Exception {
    try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT count(*) FROM artifact")) {
      return rs.next() ? rs.getInt(1) : 0;
    }
  }

  private static void execute(Connection c, String sql) throws Exception {
    try (Statement s = c.createStatement()) {
      s.execute(sql);
    }
  }

  private static Path objectPath(Path root, String sha) {
    return root.resolve(sha.substring(0, 2)).resolve(sha.substring(2, 4)).resolve(sha);
  }

  private static void setReadOnly(Path path) {
    try {
      Files.setAttribute(path, "dos:readonly", true);
    } catch (Exception ignored) {
      // non-Windows or unsupported attribute: read-only enforcement is recorded as false
    }
  }

  private static String sha256(byte[] content) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    return HexFormat.of().formatHex(digest.digest(content));
  }

  private static void copyTree(Path from, Path to) throws IOException {
    deleteRecursively(to);
    Files.createDirectories(to);
    try (var stream = Files.walk(from)) {
      for (Path source : stream.sorted(Comparator.naturalOrder()).toList()) {
        Path target = to.resolve(from.relativize(source).toString());
        if (Files.isDirectory(source)) {
          Files.createDirectories(target);
        } else {
          Files.createDirectories(target.getParent());
          Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  private static long treeSize(Path root) throws IOException {
    try (var stream = Files.walk(root)) {
      return stream.filter(Files::isRegularFile).mapToLong(path -> {
        try {
          return Files.size(path);
        } catch (IOException error) {
          return 0L;
        }
      }).sum();
    }
  }

  private static void deleteRecursively(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    try (var stream = Files.walk(root)) {
      for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
        try {
          Files.setAttribute(path, "dos:readonly", false);
        } catch (Exception ignored) {
          // not a Windows file or attribute unsupported
        }
        Files.deleteIfExists(path);
      }
    }
  }

  private static Map<String, Object> base(String strategy) {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("strategy", strategy);
    r.put("artifacts", 6);
    // 4 unique contents: small doc, 100 KB random, 5 MB random, empty file. Two duplicates.
    r.put("uniqueContents", 4);
    return r;
  }

  private static String toJson(Object value) {
    StringBuilder out = new StringBuilder();
    writeJson(value, out);
    return out.toString();
  }

  private static void writeJson(Object value, StringBuilder out) {
    if (value == null) {
      out.append("null");
    } else if (value instanceof String string) {
      out.append('"').append(string.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
    } else if (value instanceof Number || value instanceof Boolean) {
      out.append(value);
    } else if (value instanceof Map<?, ?> map) {
      out.append('{');
      boolean first = true;
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (!first) {
          out.append(',');
        }
        first = false;
        writeJson(String.valueOf(entry.getKey()), out);
        out.append(':');
        writeJson(entry.getValue(), out);
      }
      out.append('}');
    } else if (value instanceof Iterable<?> items) {
      out.append('[');
      boolean first = true;
      for (Object item : items) {
        if (!first) {
          out.append(',');
        }
        first = false;
        writeJson(item, out);
      }
      out.append(']');
    } else {
      writeJson(String.valueOf(value), out);
    }
  }
}
