package com.mammoth.dbspike;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ADR-0003 spike — compares SQLite and PostgreSQL for Mammoth M1.
 *
 * <p>For each database it records: Flyway capability, migration round-trip (idempotent re-run),
 * sample inserts, Chinese search quality (FTS/trigram vs tsvector), query latency and file/db
 * size. Output is written as UTF-8 JSON to the path given on the command line so Windows console
 * encodings cannot corrupt the evidence.
 *
 * <p>Disposable spike code. Not product code.
 */
public final class DbSpike {

  private static final String FTS_TERM = "收货流程";
  private static final String TS_TERM = "收货流程 异常";
  private static final List<String[]> DOCS = List.of(
      new String[] {"收货流程异常处理", "收货流程中条码重复时，先冻结库存再人工复核，异常需当天闭环。"},
      new String[] {"现场盘点规则", "现场盘点分冻结盘与循环盘，差异超过阈值触发复盘流程。"},
      new String[] {"数据库对象说明", "MES 核心表包含工单、库存与质检对象，收货流程写入库存台账。"});

  private DbSpike() {
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 3) {
      System.err.println("usage: sqlite <dbFile> <outJson> | pg <jdbcUrl> <user> <password> <outJson>");
      System.exit(2);
    }
    // Both drivers ship META-INF/services/java.sql.Driver; the shaded jar keeps only one of them,
    // so register both classes explicitly.
    try {
      Class.forName("org.sqlite.JDBC");
    } catch (ClassNotFoundException ignored) {
      // driver absent: reported by the connection attempt
    }
    try {
      Class.forName("org.postgresql.Driver");
    } catch (ClassNotFoundException ignored) {
      // driver absent: reported by the connection attempt
    }
    Map<String, Object> result = new LinkedHashMap<>();
    if ("sqlite".equals(args[0])) {
      result = runSqlite(Path.of(args[1]), Path.of(args[2]));
    } else if ("pg".equals(args[0])) {
      result = runPg(args[1], args[2], args[3], Path.of(args[4]));
    } else {
      throw new IllegalArgumentException("unknown mode: " + args[0]);
    }
    String json = toJson(result);
    Files.writeString(Path.of(String.valueOf(result.get("_out"))), json, StandardCharsets.UTF_8);
    System.out.println(json);
  }

  private static Map<String, Object> runSqlite(Path dbFile, Path outJson) throws Exception {
    Files.deleteIfExists(dbFile);
    String url = "jdbc:sqlite:" + dbFile.toAbsolutePath();
    Map<String, Object> r = base("sqlite", outJson);
    long start = System.nanoTime();
    String flyway = tryFlyway(url, "db/sqlite", null, null);
    r.put("flyway", flyway);
    long migrateMs = (System.nanoTime() - start) / 1_000_000;
    if (!flyway.startsWith("ok")) {
      r.put("fallbackMigration", runMiniMigrations(url, "db/sqlite"));
    }
    try (Connection c = DriverManager.getConnection(url)) {
      r.put("flywayVersions", tableExists(c, "flyway_schema_history")
          ? queryColumn(c, "SELECT version FROM flyway_schema_history ORDER BY installed_rank")
          : List.of());
      long rerunStart = System.nanoTime();
      r.put("flywayRerun", tryFlyway(url, "db/sqlite", null, null));
      r.put("flywayRerunMs", (System.nanoTime() - rerunStart) / 1_000_000);
      insertDocs(c, false);
      long ftsStart = System.nanoTime();
      int ftsHits = countFts(c, FTS_TERM);
      r.put("ftsHits", ftsHits);
      r.put("queryMs", (System.nanoTime() - ftsStart) / 1_000_000);
      r.put("likeHits", countLike(c, FTS_TERM));
      r.put("migrateMs", migrateMs);
    }
    r.put("fileSizeBytes", Files.exists(dbFile) ? Files.size(dbFile) : 0);
    // backup/restore round-trip: copy file aside, mutate, restore.
    Path backup = Path.of(dbFile.toAbsolutePath() + ".backup");
    Files.copy(dbFile, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    try (Connection c = DriverManager.getConnection(url)) {
      execute(c, "DELETE FROM knowledge_document");
      r.put("rowsAfterDelete", countRows(c, "knowledge_document"));
    }
    Files.copy(backup, dbFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    try (Connection c = DriverManager.getConnection(url)) {
      r.put("rowsAfterRestore", countRows(c, "knowledge_document"));
    }
    Files.deleteIfExists(backup);
    r.put("_out", outJson.toAbsolutePath().toString());
    return r;
  }

  private static Map<String, Object> runPg(String url, String user, String password, Path outJson) throws Exception {
    Map<String, Object> r = base("postgres", outJson);
    try (Connection probe = DriverManager.getConnection(url, user, password)) {
      execute(probe, "DROP SCHEMA public CASCADE");
      execute(probe, "CREATE SCHEMA public");
    }
    long start = System.nanoTime();
    String flyway = tryFlyway(url, "db/pg", user, password);
    r.put("flyway", flyway);
    r.put("migrateMs", (System.nanoTime() - start) / 1_000_000);
    try (Connection c = DriverManager.getConnection(url, user, password)) {
      r.put("flywayVersions", tableExists(c, "flyway_schema_history")
          ? queryColumn(c, "SELECT version FROM flyway_schema_history ORDER BY installed_rank")
          : List.of());
      long rerunStart = System.nanoTime();
      r.put("flywayRerun", tryFlyway(url, "db/pg", user, password));
      r.put("flywayRerunMs", (System.nanoTime() - rerunStart) / 1_000_000);
      insertDocs(c, true);
      long queryStart = System.nanoTime();
      r.put("trigramHits", countLike(c, "收货流程"));
      r.put("queryMs", (System.nanoTime() - queryStart) / 1_000_000);
      r.put("tsqueryHits", countTsquery(c, TS_TERM));
      r.put("similarityTop", similarityTop(c, "收货流程 异常"));
    }
    r.put("_out", outJson.toAbsolutePath().toString());
    return r;
  }

  private static Map<String, Object> base(String db, Path outJson) {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("db", db);
    r.put("_out", outJson.toAbsolutePath().toString());
    return r;
  }

  /** Returns "ok" or "unsupported: message". */
  private static String tryFlyway(String url, String location, String user, String password) {
    try {
      org.flywaydb.core.Flyway flyway = org.flywaydb.core.Flyway.configure()
          .dataSource(url, user, password)
          .locations("classpath:" + location)
          .load();
      var result = flyway.migrate();
      return "ok:applied=" + result.migrationsExecuted + ",total=" + result.migrationsExecuted;
    } catch (Throwable error) {
      String message = error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
      return "unsupported: " + message.replace('\n', ' ');
    }
  }

  /** Fallback migration runner for databases Flyway does not support (records schema_version). */
  private static String runMiniMigrations(String url, String location) throws Exception {
    List<String> applied = new ArrayList<>();
    try (Connection c = DriverManager.getConnection(url)) {
      execute(c, "CREATE TABLE IF NOT EXISTS schema_version (version TEXT PRIMARY KEY, applied_at TEXT NOT NULL)");
      for (String file : List.of("V1__init.sql", "V2__add_index.sql")) {
        String version = file.substring(1, file.indexOf("__"));
        if (!queryColumn(c, "SELECT version FROM schema_version WHERE version='" + version + "'").isEmpty()) {
          continue;
        }
        executeScript(c, readResource(location + "/" + file));
        execute(c, "INSERT INTO schema_version VALUES ('" + version + "', datetime('now'))");
        applied.add(version);
      }
      return "applied=" + applied;
    }
  }

  private static void insertDocs(Connection c, boolean postgres) throws Exception {
    execute(c, "INSERT INTO project VALUES ('PRJ-001','ACME-MES-2026')");
    String sql = postgres
        ? "INSERT INTO knowledge_document (project_id,title,body,status,body_tsv) VALUES (?,?,?,'verified',to_tsvector('simple', ?))"
        : "INSERT INTO knowledge_document (project_id,title,body,status) VALUES (?,?,?,'verified')";
    try (PreparedStatement ps = c.prepareStatement(sql)) {
      for (String[] doc : DOCS) {
        ps.setString(1, "PRJ-001");
        ps.setString(2, doc[0]);
        ps.setString(3, doc[1]);
        if (postgres) {
          ps.setString(4, doc[1]);
        }
        ps.addBatch();
      }
      ps.executeBatch();
    }
    if (!postgres) {
      try (PreparedStatement ps = c.prepareStatement(
          "INSERT INTO knowledge_fts (text) SELECT title || ' ' || body FROM knowledge_document")) {
        ps.executeUpdate();
      }
    }
  }

  private static int countFts(Connection c, String term) throws Exception {
    try (PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM knowledge_fts WHERE knowledge_fts MATCH ?")) {
      ps.setString(1, term);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getInt(1) : 0;
      }
    }
  }

  private static int countLike(Connection c, String term) throws Exception {
    try (PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM knowledge_document WHERE body LIKE ?")) {
      ps.setString(1, "%" + term + "%");
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getInt(1) : 0;
      }
    }
  }

  private static int countTsquery(Connection c, String term) throws Exception {
    try (PreparedStatement ps = c.prepareStatement(
        "SELECT count(*) FROM knowledge_document WHERE body_tsv @@ plainto_tsquery('simple', ?)")) {
      ps.setString(1, term);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getInt(1) : 0;
      }
    }
  }

  private static List<String> similarityTop(Connection c, String term) throws Exception {
    List<String> titles = new ArrayList<>();
    try (PreparedStatement ps = c.prepareStatement(
        "SELECT title FROM knowledge_document ORDER BY similarity(body, ?) DESC LIMIT 3")) {
      ps.setString(1, term);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          titles.add(rs.getString(1));
        }
      }
    }
    return titles;
  }

  private static int countRows(Connection c, String table) throws Exception {
    try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT count(*) FROM " + table)) {
      return rs.next() ? rs.getInt(1) : 0;
    }
  }

  private static boolean tableExists(Connection c, String table) {
    try (ResultSet rs = c.getMetaData().getTables(null, null, table, null)) {
      if (rs.next()) {
        return true;
      }
    } catch (SQLException ignored) {
      // fall through to the lower-case probe
    }
    try (ResultSet rs = c.getMetaData().getTables(null, null, table.toUpperCase(), null)) {
      return rs.next();
    } catch (SQLException ignored) {
      return false;
    }
  }

  private static List<String> queryColumn(Connection c, String sql) throws Exception {
    List<String> values = new ArrayList<>();
    try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
      while (rs.next()) {
        values.add(rs.getString(1));
      }
    }
    return values;
  }

  private static void execute(Connection c, String sql) throws SQLException {
    try (Statement s = c.createStatement()) {
      s.execute(sql);
    }
  }

  private static void executeScript(Connection c, String script) throws Exception {
    StringBuilder withoutComments = new StringBuilder();
    for (String line : script.split("\n")) {
      String trimmedLine = line.trim();
      if (trimmedLine.startsWith("--")) {
        continue;
      }
      withoutComments.append(line).append('\n');
    }
    for (String statement : withoutComments.toString().split(";")) {
      String trimmed = statement.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      execute(c, trimmed);
    }
  }

  private static String readResource(String path) throws Exception {
    try (InputStream in = DbSpike.class.getClassLoader().getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("missing resource: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
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
      out.append('"').append(string.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")).append('"');
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
