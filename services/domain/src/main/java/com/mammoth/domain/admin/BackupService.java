package com.mammoth.domain.admin;

import com.mammoth.domain.artifact.ArtifactStore;
import com.mammoth.domain.config.DataRootBootstrap;
import com.mammoth.domain.support.Ids;
import com.mammoth.domain.support.JsonLite;
import com.mammoth.domain.web.ApiException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Online-safe backup (ADR-0003/0008): SQLite {@code VACUUM INTO} on a dedicated connection (no open
 * statements) plus artifact bytes verified by SHA-256, with a machine-checkable manifest.
 */
@Service
public class BackupService {

  static final String FORMAT_VERSION = "1";
  private static final Logger log = LoggerFactory.getLogger(BackupService.class);

  private final DataRootBootstrap dataRoot;
  private final DataSource dataSource;
  private final ArtifactStore artifactStore;

  public BackupService(DataRootBootstrap dataRoot, DataSource dataSource,
      ArtifactStore artifactStore) {
    this.dataRoot = dataRoot;
    this.dataSource = dataSource;
    this.artifactStore = artifactStore;
  }

  public record Result(Path directory, long databaseBytes, int artifacts, long schemaVersion) {
  }

  public Result backup(Path targetParent) {
    Path parent = targetParent.toAbsolutePath().normalize();
    if (parent.startsWith(dataRoot.root())) {
      throw ApiException.badRequest(
          "backup target must be outside the Mammoth data root: " + parent);
    }
    Path directory = parent.resolve("mammoth-backup-" + Ids.nowIso().replace(':', '-'));
    Path databaseCopy = directory.resolve("db").resolve("mammoth.db");
    try {
      Files.createDirectories(databaseCopy.getParent());
    } catch (IOException error) {
      throw new IllegalStateException("cannot create backup directory: " + directory, error);
    }

    long schemaVersion;
    try (Connection connection = dataSource.getConnection();
         Statement statement = connection.createStatement()) {
      schemaVersion = schemaVersion(statement);
      String target = databaseCopy.toAbsolutePath().toString().replace('\\', '/')
          .replace("'", "''");
      // VACUUM INTO requires no statements in progress in this transaction.
      statement.execute("VACUUM INTO '" + target + "'");
    } catch (SQLException error) {
      throw new IllegalStateException("database backup failed: " + error.getMessage(), error);
    }

    Map<String, String> artifactHashes = new LinkedHashMap<>();
    int artifactCount = 0;
    try (Connection connection = dataSource.getConnection();
         Statement statement = connection.createStatement();
         ResultSet resultSet = statement.executeQuery(
             "SELECT content_hash, storage_path FROM artifact ORDER BY content_hash")) {
      while (resultSet.next()) {
        String hash = resultSet.getString(1);
        String storagePath = resultSet.getString(2);
        byte[] bytes = artifactStore.read(hash);
        if (!ExportService.sha256(bytes).equals(hash)) {
          throw ApiException.conflict("artifact checksum mismatch while backing up: " + hash);
        }
        Path relative = Path.of(storagePath);
        if (relative.isAbsolute() || relative.startsWith("..")) {
          throw ApiException.conflict("unsafe artifact storage path in database: " + storagePath);
        }
        Path targetFile = directory.resolve(relative).normalize();
        if (!targetFile.startsWith(directory)) {
          throw ApiException.conflict("artifact path escapes the backup: " + storagePath);
        }
        Files.createDirectories(targetFile.getParent());
        Files.write(targetFile, bytes);
        artifactHashes.put(storagePath.replace('\\', '/'), hash);
        artifactCount++;
      }
    } catch (IOException | SQLException error) {
      throw new IllegalStateException("artifact backup failed: " + error.getMessage(), error);
    }

    try {
      long databaseBytes = Files.size(databaseCopy);
      Properties manifest = new Properties();
      manifest.setProperty("formatVersion", FORMAT_VERSION);
      manifest.setProperty("createdAt", Ids.nowIso());
      manifest.setProperty("schemaVersion", String.valueOf(schemaVersion));
      manifest.setProperty("databaseFile", "db/mammoth.db");
      manifest.setProperty("databaseSha256", sha256File(databaseCopy));
      manifest.setProperty("databaseBytes", String.valueOf(databaseBytes));
      manifest.setProperty("artifactCount", String.valueOf(artifactCount));
      for (Map.Entry<String, String> entry : artifactHashes.entrySet()) {
        manifest.setProperty("sha256." + entry.getKey(), entry.getValue());
      }
      try (OutputStream output = Files.newOutputStream(directory.resolve("manifest.properties"))) {
        manifest.store(output, "Mammoth backup manifest (ADR-0008)");
      }
      audit("admin.backup", JsonLite.write(JsonLite.object(
          "directory", directory.getFileName().toString(),
          "databaseBytes", databaseBytes,
          "artifacts", artifactCount)));
      log.info("backup written: {} ({} artifacts, {} bytes)", directory, artifactCount,
          databaseBytes);
      return new Result(directory, databaseBytes, artifactCount, schemaVersion);
    } catch (IOException | SQLException error) {
      throw new IllegalStateException("cannot write backup manifest: " + error.getMessage(), error);
    }
  }

  private long schemaVersion(Statement statement) throws SQLException {
    try (ResultSet resultSet = statement.executeQuery(
        "SELECT MAX(CAST(version AS INTEGER)) FROM flyway_schema_history WHERE success = 1")) {
      return resultSet.next() ? resultSet.getLong(1) : 0;
    }
  }

  private void audit(String action, String detailJson) throws SQLException {
    try (Connection connection = dataSource.getConnection();
         PreparedStatement statement = connection.prepareStatement(
             "INSERT INTO audit_log(id, actor_user_id, action, object_type, object_id, at,"
                 + " detail_json) VALUES(?,?,?,?,?,?,?)")) {
      statement.setString(1, Ids.newId());
      statement.setString(2, null);
      statement.setString(3, action);
      statement.setString(4, "admin");
      statement.setString(5, null);
      statement.setString(6, Ids.nowIso());
      statement.setString(7, detailJson);
      statement.executeUpdate();
    }
  }

  static String sha256File(Path file) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(Files.readAllBytes(file));
      StringBuilder hex = new StringBuilder();
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (Exception error) {
      throw new IllegalStateException("SHA-256 unavailable", error);
    }
  }

}
