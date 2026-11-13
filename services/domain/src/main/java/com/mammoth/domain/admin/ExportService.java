package com.mammoth.domain.admin;

import com.mammoth.domain.artifact.ArtifactStore;
import com.mammoth.domain.config.DataRootBootstrap;
import com.mammoth.domain.support.Ids;
import com.mammoth.domain.support.JsonLite;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Logical export package (ADR-0008): a zip with per-table SQL dumps, immutable artifact bytes and a
 * machine-checkable manifest. Password hashes and sessions are never exported.
 */
@Service
public class ExportService {

  static final String FORMAT_VERSION = "1";
  private static final Logger log = LoggerFactory.getLogger(ExportService.class);

  private final DataSource dataSource;
  private final DataRootBootstrap dataRoot;
  private final ArtifactStore artifactStore;

  public ExportService(DataSource dataSource, DataRootBootstrap dataRoot,
      ArtifactStore artifactStore) {
    this.dataSource = dataSource;
    this.dataRoot = dataRoot;
    this.artifactStore = artifactStore;
  }

  public record Result(Path zip, int tables, long rows, int artifacts, long sizeBytes) {
  }

  public Result exportPackage(Path targetZip, String actorUserId) {
    Path target = targetZip == null
        ? dataRoot.root().resolve("exports")
            .resolve(Ids.nowIso().replace(':', '-') + "-mammoth-export.zip")
        : targetZip.toAbsolutePath().normalize();
    if (target.getParent() != null) {
      try {
        Files.createDirectories(target.getParent());
      } catch (IOException error) {
        throw new IllegalStateException("cannot create export directory: " + target.getParent(),
            error);
      }
    }
    Path temp = target.resolveSibling(target.getFileName() + ".tmp");
    Map<String, String> hashes = new LinkedHashMap<>();
    Map<String, Long> rowCounts = new LinkedHashMap<>();
    List<String> artifactHashes = new ArrayList<>();

    try (Connection connection = dataSource.getConnection();
         ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp),
             StandardCharsets.UTF_8)) {
      long schemaVersion = schemaVersion(connection);
      for (String table : AdminTables.TABLES) {
        byte[] dump = dumpTable(connection, table, rowCounts);
        putEntry(zip, "data/" + table + ".sql", dump, hashes);
      }
      try (Statement statement = connection.createStatement();
           ResultSet resultSet = statement.executeQuery(
               "SELECT content_hash FROM artifact ORDER BY content_hash")) {
        while (resultSet.next()) {
          String hash = resultSet.getString(1);
          byte[] bytes = artifactStore.read(hash);
          putEntry(zip, "artifacts/" + hash, bytes, hashes);
          artifactHashes.add(hash);
        }
      }

      Properties manifest = new Properties();
      manifest.setProperty("formatVersion", FORMAT_VERSION);
      manifest.setProperty("createdAt", Ids.nowIso());
      manifest.setProperty("appVersion", applicationVersion());
      manifest.setProperty("schemaVersion", String.valueOf(schemaVersion));
      manifest.setProperty("tableCount", String.valueOf(AdminTables.TABLES.size()));
      manifest.setProperty("artifactCount", String.valueOf(artifactHashes.size()));
      long totalRows = 0;
      for (Map.Entry<String, Long> entry : rowCounts.entrySet()) {
        manifest.setProperty("rowCount." + entry.getKey(), String.valueOf(entry.getValue()));
        totalRows += entry.getValue();
      }
      manifest.setProperty("rowTotal", String.valueOf(totalRows));
      for (Map.Entry<String, String> entry : hashes.entrySet()) {
        manifest.setProperty("sha256." + entry.getKey(), entry.getValue());
      }
      ByteArrayOutputStream manifestBytes = new ByteArrayOutputStream();
      manifest.store(manifestBytes, "Mammoth export manifest (ADR-0008)");
      zip.putNextEntry(new ZipEntry("manifest.properties"));
      zip.write(manifestBytes.toByteArray());
      zip.closeEntry();
      zip.finish();

      Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
      long size = Files.size(target);
      audit(connection, actorUserId, "admin.export", JsonLite.write(JsonLite.object(
          "zip", target.getFileName().toString(),
          "artifacts", artifactHashes.size(),
          "rows", totalRows,
          "schemaVersion", schemaVersion)));
      log.info("export written: {} ({} tables, {} artifacts, {} bytes)", target,
          AdminTables.TABLES.size(), artifactHashes.size(), size);
      return new Result(target, AdminTables.TABLES.size(), totalRows, artifactHashes.size(), size);
    } catch (IOException | SQLException error) {
      try {
        Files.deleteIfExists(temp);
      } catch (IOException ignored) {
        // best effort cleanup
      }
      throw new IllegalStateException("export failed: " + error.getMessage(), error);
    }
  }

  private static long schemaVersion(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement();
         ResultSet resultSet = statement.executeQuery(
             "SELECT MAX(CAST(version AS INTEGER)) FROM flyway_schema_history WHERE success = 1")) {
      return resultSet.next() ? resultSet.getLong(1) : 0;
    }
  }

  private static byte[] dumpTable(Connection connection, String table, Map<String, Long> counts)
      throws SQLException {
    StringBuilder out = new StringBuilder();
    long rows = 0;
    boolean appUser = "app_user".equals(table);
    try (Statement statement = connection.createStatement();
         ResultSet resultSet = statement.executeQuery("SELECT * FROM \"" + table + "\"")) {
      ResultSetMetaData meta = resultSet.getMetaData();
      int columns = meta.getColumnCount();
      List<String> columnNames = new ArrayList<>();
      for (int index = 1; index <= columns; index++) {
        columnNames.add(meta.getColumnName(index));
      }
      while (resultSet.next()) {
        out.append("INSERT INTO \"").append(table).append("\" (");
        for (int index = 0; index < columns; index++) {
          if (index > 0) {
            out.append(',');
          }
          out.append('"').append(columnNames.get(index)).append('"');
        }
        out.append(") VALUES (");
        for (int index = 1; index <= columns; index++) {
          if (index > 1) {
            out.append(',');
          }
          String column = columnNames.get(index - 1);
          if (appUser && "password_hash".equalsIgnoreCase(column)) {
            out.append(sqlLiteral(AdminTables.LOCKED_PASSWORD));
            continue;
          }
          Object value = resultSet.getObject(index);
          out.append(sqlLiteral(value));
        }
        out.append(");\n");
        rows++;
      }
    }
    counts.put(table, rows);
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }

  static String sqlLiteral(Object value) {
    if (value == null) {
      return "NULL";
    }
    if (value instanceof Number || value instanceof Boolean) {
      return String.valueOf(value);
    }
    if (value instanceof byte[] bytes) {
      StringBuilder hex = new StringBuilder("X'");
      for (byte b : bytes) {
        hex.append(String.format("%02x", b));
      }
      return hex.append('\'').toString();
    }
    String text = value.toString().replace("'", "''");
    return "'" + text + "'";
  }

  private static void putEntry(ZipOutputStream zip, String name, byte[] bytes,
      Map<String, String> hashes) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(bytes);
    zip.closeEntry();
    hashes.put(name, sha256(bytes));
  }

  static String sha256(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      StringBuilder hex = new StringBuilder();
      for (byte b : digest.digest(bytes)) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (Exception error) {
      throw new IllegalStateException("SHA-256 unavailable", error);
    }
  }

  private static String applicationVersion() {
    String version = ExportService.class.getPackage().getImplementationVersion();
    return version == null || version.isBlank() ? "dev" : version;
  }

  private static void audit(Connection connection, String actorUserId, String action,
      String detailJson) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        "INSERT INTO audit_log(id, actor_user_id, action, object_type, object_id, at, detail_json)"
            + " VALUES(?,?,?,?,?,?,?)")) {
      statement.setString(1, Ids.newId());
      statement.setString(2, actorUserId);
      statement.setString(3, action);
      statement.setString(4, "admin");
      statement.setString(5, null);
      statement.setString(6, Ids.nowIso());
      statement.setString(7, detailJson);
      statement.executeUpdate();
    }
  }

  /** Default export path used by the admin CLI when no explicit target is given. */
  public Path defaultExportPath() {
    return dataRoot.root().resolve("exports")
        .resolve(Ids.nowIso().replace(':', '-') + "-mammoth-export.zip");
  }

}
