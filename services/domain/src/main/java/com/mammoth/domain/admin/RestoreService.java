package com.mammoth.domain.admin;

import com.mammoth.domain.web.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import org.sqlite.SQLiteConfig;

/**
 * Restores a {@link BackupService} backup into a new empty data root (ADR-0008). Pure file
 * operation with checksum + SQLite integrity validation, so it runs without starting the service.
 */
public final class RestoreService {

  public record Result(Path targetRoot, long databaseBytes, int artifacts) {
  }

  public Result restore(Path backupDirectory, Path targetRoot) {
    Path backup = backupDirectory.toAbsolutePath().normalize();
    Path target = targetRoot.toAbsolutePath().normalize();
    Path manifestFile = backup.resolve("manifest.properties");
    if (!Files.isRegularFile(manifestFile)) {
      throw ApiException.badRequest("backup manifest not found: " + manifestFile);
    }
    Properties manifest = loadManifest(manifestFile);
    if (!BackupService.FORMAT_VERSION.equals(manifest.getProperty("formatVersion"))) {
      throw ApiException.badRequest(
          "unsupported backup formatVersion: " + manifest.getProperty("formatVersion"));
    }
    Path databaseSource = backup.resolve(require(manifest, "databaseFile")).normalize();
    if (!databaseSource.startsWith(backup) || !Files.isRegularFile(databaseSource)) {
      throw ApiException.badRequest("backup database file is missing: " + databaseSource);
    }
    Map<Path, String> artifacts = artifactFiles(backup, manifest);
    validateDatabase(databaseSource, manifest, backup);
    validateArtifacts(artifacts);

    requireEmptyTarget(target);
    long databaseBytes;
    try {
      Path targetDb = target.resolve("db").resolve("mammoth.db");
      Files.createDirectories(targetDb.getParent());
      Files.createDirectories(target.resolve("artifacts"));
      Files.createDirectories(target.resolve("cache"));
      Files.copy(databaseSource, targetDb, StandardCopyOption.REPLACE_EXISTING);
      databaseBytes = Files.size(targetDb);
      for (Map.Entry<Path, String> entry : artifacts.entrySet()) {
        Path relative = backup.relativize(entry.getKey());
        Path targetFile = target.resolve(relative).normalize();
        if (!targetFile.startsWith(target)) {
          throw ApiException.badRequest("artifact path escapes the target root: " + relative);
        }
        Files.createDirectories(targetFile.getParent());
        Files.copy(entry.getKey(), targetFile, StandardCopyOption.REPLACE_EXISTING);
        if (!entry.getValue().equals(BackupService.sha256File(targetFile))) {
          throw ApiException.conflict("restored artifact checksum mismatch: " + relative);
        }
      }
      Files.copy(manifestFile, target.resolve("backup-manifest.properties"),
          StandardCopyOption.REPLACE_EXISTING);
      Properties restore = new Properties();
      restore.setProperty("restoredAt", java.time.Instant.now().toString());
      restore.setProperty("backupDirectory", backup.toString());
      restore.setProperty("schemaVersion", manifest.getProperty("schemaVersion", ""));
      try (var output = Files.newOutputStream(target.resolve("restore.properties"))) {
        restore.store(output, "Mammoth restore record (ADR-0008)");
      }
    } catch (IOException error) {
      throw new IllegalStateException("restore failed: " + error.getMessage(), error);
    }
    return new Result(target, databaseBytes, artifacts.size());
  }

  private static Properties loadManifest(Path manifestFile) {
    Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(manifestFile);
         InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
      properties.load(reader);
    } catch (IOException error) {
      throw new IllegalStateException("cannot read backup manifest: " + error.getMessage(), error);
    }
    return properties;
  }

  private static Map<Path, String> artifactFiles(Path backup, Properties manifest) {
    Map<Path, String> artifacts = new LinkedHashMap<>();
    for (String key : manifest.stringPropertyNames()) {
      if (!key.startsWith("sha256.artifacts/")) {
        continue;
      }
      String hash = manifest.getProperty(key);
      String relative = key.substring("sha256.".length()).replace('/', java.io.File.separatorChar);
      Path file = backup.resolve(relative).normalize();
      if (!file.startsWith(backup)) {
        throw ApiException.badRequest("artifact path escapes the backup: " + key);
      }
      artifacts.put(file, hash);
    }
    return artifacts;
  }

  private static void validateDatabase(Path database, Properties manifest, Path backup) {
    try {
      String expected = require(manifest, "databaseSha256");
      if (!expected.equals(BackupService.sha256File(database))) {
        throw ApiException.conflict("backup database checksum mismatch");
      }
      SQLiteConfig config = new SQLiteConfig();
      config.setReadOnly(true);
      try (Connection connection = DriverManager.getConnection(
          "jdbc:sqlite:" + database.toAbsolutePath(), config.toProperties());
           Statement statement = connection.createStatement()) {
        try (ResultSet resultSet = statement.executeQuery("PRAGMA integrity_check")) {
          if (!resultSet.next() || !"ok".equalsIgnoreCase(resultSet.getString(1))) {
            throw ApiException.conflict("backup database failed SQLite integrity_check");
          }
        }
        long expectedVersion = Long.parseLong(require(manifest, "schemaVersion"));
        try (ResultSet resultSet = statement.executeQuery(
            "SELECT MAX(CAST(version AS INTEGER)) FROM flyway_schema_history WHERE success = 1")) {
          long actual = resultSet.next() ? resultSet.getLong(1) : 0;
          if (actual != expectedVersion) {
            throw ApiException.conflict(
                "backup schema v" + actual + " does not match manifest v" + expectedVersion);
          }
        }
      }
    } catch (SQLException | IOException error) {
      throw new IllegalStateException("cannot validate backup database: " + error.getMessage(),
          error);
    }
  }

  private static void validateArtifacts(Map<Path, String> artifacts) {
    for (Map.Entry<Path, String> entry : artifacts.entrySet()) {
      if (!Files.isRegularFile(entry.getKey())) {
        throw ApiException.badRequest("backup artifact is missing: " + entry.getKey());
      }
      try {
        if (!entry.getValue().equals(BackupService.sha256File(entry.getKey()))) {
          throw ApiException.conflict("backup artifact checksum mismatch: " + entry.getKey());
        }
      } catch (IOException error) {
        throw new IllegalStateException("cannot read backup artifact: " + entry.getKey(), error);
      }
    }
  }

  private static void requireEmptyTarget(Path target) {
    if (!Files.exists(target)) {
      return;
    }
    if (!Files.isDirectory(target)) {
      throw ApiException.badRequest("restore target is not a directory: " + target);
    }
    try (Stream<Path> entries = Files.list(target)) {
      if (entries.findAny().isPresent()) {
        throw ApiException.conflict(
            "restore target must be an empty directory: " + target);
      }
    } catch (IOException error) {
      throw new IllegalStateException("cannot inspect restore target: " + target, error);
    }
  }

  private static String require(Properties manifest, String key) {
    String value = manifest.getProperty(key);
    if (value == null || value.isBlank()) {
      throw ApiException.badRequest("backup manifest is missing " + key);
    }
    return value;
  }
}
