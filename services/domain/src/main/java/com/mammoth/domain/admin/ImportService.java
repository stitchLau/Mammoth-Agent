package com.mammoth.domain.admin;

import com.mammoth.domain.artifact.ArtifactStore;
import com.mammoth.domain.support.Ids;
import com.mammoth.domain.support.JsonLite;
import com.mammoth.domain.web.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Imports an export package into an empty data root (ADR-0008). Refuses non-empty databases,
 * validates every checksum, never imports sessions and keeps imported accounts locked until the
 * operator supplies credentials for one administrator.
 */
@Service
public class ImportService {

  private static final Logger log = LoggerFactory.getLogger(ImportService.class);

  private final DataSource dataSource;
  private final ArtifactStore artifactStore;
  private final PasswordEncoder passwordEncoder;

  public ImportService(DataSource dataSource, ArtifactStore artifactStore,
      PasswordEncoder passwordEncoder) {
    this.dataSource = dataSource;
    this.artifactStore = artifactStore;
    this.passwordEncoder = passwordEncoder;
  }

  public record Result(int tables, long rows, int artifacts, String adminUsername) {
  }

  public Result importPackage(Path zipPath, String adminUsername, String adminPassword) {
    Path packageFile = zipPath == null ? null : zipPath.toAbsolutePath().normalize();
    if (packageFile == null || !Files.isRegularFile(packageFile)) {
      throw ApiException.badRequest("export package not found: " + zipPath);
    }
    String username = requireText(adminUsername, "adminUsername");
    String password = requireText(adminPassword, "adminPassword");
    if (password.length() < 8) {
      throw ApiException.badRequest("adminPassword must be at least 8 characters");
    }

    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        try (Statement statement = connection.createStatement()) {
          statement.execute("PRAGMA foreign_keys=ON");
          statement.execute("PRAGMA defer_foreign_keys=ON");
        }
        requireEmptyDatabase(connection);
        try (ZipFile zip = new ZipFile(packageFile.toFile(), StandardCharsets.UTF_8)) {
          Properties manifest = readManifest(zip);
          validateManifest(manifest, connection);
          verifyEntries(zip, manifest);

          Map<String, Integer> importedArtifacts = importArtifacts(zip, manifest);
          long rows = importTables(zip, manifest, connection);
          validateArtifactRows(connection, importedArtifacts);
          String adminUserId = setAdminPassword(connection, username, password);
          audit(connection, adminUserId, "admin.import", JsonLite.write(JsonLite.object(
              "zip", packageFile.getFileName().toString(),
              "artifacts", importedArtifacts.size(),
              "rows", rows)));
          connection.commit();
          log.info("import complete: {} tables, {} rows, {} artifacts", AdminTables.TABLES.size(),
              rows, importedArtifacts.size());
          return new Result(AdminTables.TABLES.size(), rows, importedArtifacts.size(), username);
        }
      } catch (IOException | SQLException error) {
        rollback(connection);
        throw new IllegalStateException("import failed: " + error.getMessage(), error);
      } catch (RuntimeException error) {
        rollback(connection);
        throw error;
      }
    } catch (SQLException error) {
      throw new IllegalStateException("cannot open the target database: " + error.getMessage(),
          error);
    }
  }

  private static void rollback(Connection connection) {
    try {
      connection.rollback();
    } catch (SQLException ignored) {
      // connection is already broken
    }
  }

  private static void requireEmptyDatabase(Connection connection) throws SQLException {
    for (String table : AdminTables.TABLES) {
      try (Statement statement = connection.createStatement();
           ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM \"" + table + "\"")) {
        long count = resultSet.next() ? resultSet.getLong(1) : 0;
        if (count > 0) {
          throw ApiException.conflict(
              "target data root is not empty: table " + table + " has " + count
                  + " rows. Import requires an empty database (ADR-0008).");
        }
      }
    }
  }

  private static Properties readManifest(ZipFile zip) throws IOException {
    ZipEntry entry = zip.getEntry("manifest.properties");
    if (entry == null) {
      throw ApiException.badRequest("export package has no manifest.properties");
    }
    Properties properties = new Properties();
    try (InputStream input = zip.getInputStream(entry);
         InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
      properties.load(reader);
    }
    return properties;
  }

  private static void validateManifest(Properties manifest, Connection connection)
      throws SQLException {
    String format = manifest.getProperty("formatVersion");
    if (!ExportService.FORMAT_VERSION.equals(format)) {
      throw ApiException.badRequest("unsupported export formatVersion: " + format);
    }
    long required = currentSchemaVersion(connection);
    long exported = Long.parseLong(manifest.getProperty("schemaVersion", "-1"));
    if (exported != required) {
      throw ApiException.conflict(
          "export schema v" + exported + " does not match the current schema v" + required
              + "; align the application versions before importing");
    }
  }

  private static long currentSchemaVersion(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement();
         ResultSet resultSet = statement.executeQuery(
             "SELECT MAX(CAST(version AS INTEGER)) FROM flyway_schema_history WHERE success = 1")) {
      return resultSet.next() ? resultSet.getLong(1) : 0;
    }
  }

  /** Verify every zip entry is expected, checksummed and that all tables are present. */
  private static void verifyEntries(ZipFile zip, Properties manifest) throws IOException {
    List<String> expected = new ArrayList<>();
    for (String table : AdminTables.TABLES) {
      expected.add("data/" + table + ".sql");
    }
    for (String name : manifest.stringPropertyNames()) {
      if (name.startsWith("sha256.artifacts/")) {
        expected.add(name.substring("sha256.".length()));
      }
    }
    java.util.Set<String> expectedSet = new java.util.HashSet<>(expected);
    var entries = zip.entries();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      String name = entry.getName();
      if ("manifest.properties".equals(name)) {
        continue;
      }
      if (!expectedSet.contains(name)) {
        throw ApiException.badRequest("unexpected entry in export package: " + name);
      }
      String checksum = manifest.getProperty("sha256." + name);
      if (checksum == null) {
        throw ApiException.badRequest("export package entry has no checksum: " + name);
      }
      byte[] bytes = readEntry(zip, name);
      if (!checksum.equals(ExportService.sha256(bytes))) {
        throw ApiException.conflict("checksum mismatch for export package entry: " + name);
      }
    }
    for (String name : expected) {
      if (zip.getEntry(name) == null) {
        throw ApiException.badRequest("export package is missing entry: " + name);
      }
    }
  }

  private Map<String, Integer> importArtifacts(ZipFile zip, Properties manifest)
      throws IOException {
    Map<String, Integer> sizes = new LinkedHashMap<>();
    for (String name : manifest.stringPropertyNames()) {
      if (!name.startsWith("sha256.artifacts/")) {
        continue;
      }
      String hash = name.substring("sha256.artifacts/".length());
      if (hash.length() != 64) {
        throw ApiException.badRequest("invalid artifact hash in manifest: " + hash);
      }
      byte[] bytes = readEntry(zip, "artifacts/" + hash);
      if (!ExportService.sha256(bytes).equals(hash)) {
        throw ApiException.conflict("artifact checksum mismatch: " + hash);
      }
      ArtifactStore.StoredArtifact stored = artifactStore.put(bytes);
      if (!hash.equals(stored.sha256()) || stored.sizeBytes() != bytes.length) {
        throw ApiException.conflict("artifact store rejected content: " + hash);
      }
      sizes.put(hash, bytes.length);
    }
    return sizes;
  }

  private static long importTables(ZipFile zip, Properties manifest, Connection connection)
      throws IOException, SQLException {
    long totalRows = 0;
    for (String table : AdminTables.TABLES) {
      String dump = new String(readEntry(zip, "data/" + table + ".sql"), StandardCharsets.UTF_8);
      long tableRows = 0;
      for (String line : dump.split("\n")) {
        String sql = line.trim();
        if (sql.isEmpty()) {
          continue;
        }
        try (Statement statement = connection.createStatement()) {
          statement.executeUpdate(sql);
        } catch (SQLException error) {
          throw new SQLException(
              "failed to import table " + table + ": " + error.getMessage(), error);
        }
        tableRows++;
      }
      String expected = manifest.getProperty("rowCount." + table);
      if (expected != null && Long.parseLong(expected) != tableRows) {
        throw ApiException.conflict(
            "row count mismatch for " + table + " (manifest " + expected + ", actual "
                + tableRows + ")");
      }
      totalRows += tableRows;
    }
    return totalRows;
  }

  private static void validateArtifactRows(Connection connection, Map<String, Integer> imported)
      throws SQLException {
    try (Statement statement = connection.createStatement();
         ResultSet resultSet = statement.executeQuery(
             "SELECT content_hash, size_bytes FROM artifact")) {
      while (resultSet.next()) {
        String hash = resultSet.getString(1);
        int size = resultSet.getInt(2);
        Integer importedSize = imported.get(hash);
        if (importedSize == null) {
          throw ApiException.conflict(
              "artifact metadata has no bytes in the package: " + hash);
        }
        if (importedSize != size) {
          throw ApiException.conflict(
              "artifact size mismatch for " + hash + " (row " + size + ", bytes " + importedSize
                  + ")");
        }
      }
    }
  }

  private String setAdminPassword(Connection connection, String username, String password)
      throws SQLException {
    String userId;
    try (PreparedStatement statement = connection.prepareStatement(
        "SELECT id FROM app_user WHERE username = ?")) {
      statement.setString(1, username);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw ApiException.badRequest(
              "adminUsername does not exist in the export: " + username);
        }
        userId = resultSet.getString(1);
      }
    }
    try (PreparedStatement statement = connection.prepareStatement(
        "UPDATE app_user SET password_hash = ? WHERE id = ?")) {
      statement.setString(1, passwordEncoder.encode(password));
      statement.setString(2, userId);
      statement.executeUpdate();
    }
    return userId;
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

  private static byte[] readEntry(ZipFile zip, String name) throws IOException {
    ZipEntry entry = zip.getEntry(name);
    if (entry == null) {
      throw ApiException.badRequest("export package is missing entry: " + name);
    }
    try (InputStream input = zip.getInputStream(entry)) {
      return input.readAllBytes();
    }
  }

  private static String requireText(String value, String field) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isEmpty()) {
      throw ApiException.badRequest(field + " is required");
    }
    return normalized;
  }
}
