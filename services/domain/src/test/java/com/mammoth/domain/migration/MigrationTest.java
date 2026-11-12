package com.mammoth.domain.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T3 acceptance: the V1 migration applies cleanly to an empty SQLite database, is idempotent on a
 * second run, and creates the M1 baseline tables. This runs without a Spring context so CI catches
 * migration breakage even when application wiring changes.
 */
class MigrationTest {

  private static final List<String> EXPECTED_TABLES = List.of(
      "organization", "app_user", "role_assignment", "customer", "product", "project",
      "project_product", "project_member", "artifact", "knowledge_document",
      "knowledge_source_ref", "knowledge_chunk", "audit_log", "session");

  @TempDir
  Path tempDir;

  @Test
  void appliesMigrationsAndIsIdempotent() throws Exception {
    String url = "jdbc:sqlite:" + tempDir.resolve("migration-test.db").toAbsolutePath();
    Flyway flyway = Flyway.configure()
        .dataSource(url, null, null)
        .locations("classpath:db/migration")
        .load();

    MigrateResult first = flyway.migrate();
    assertThat(first.migrationsExecuted).isEqualTo(3);
    assertThat(first.targetSchemaVersion).isEqualTo("3");

    MigrateResult second = flyway.migrate();
    assertThat(second.migrationsExecuted).isZero();

    try (Connection connection = DriverManager.getConnection(url);
         Statement statement = connection.createStatement()) {
      List<String> tables = new ArrayList<>();
      try (ResultSet resultSet = statement.executeQuery(
          "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
              + " AND name <> 'flyway_schema_history' ORDER BY name")) {
        while (resultSet.next()) {
          tables.add(resultSet.getString(1));
        }
      }
      assertThat(tables).containsAll(EXPECTED_TABLES);
    }
  }

  @Test
  void migrationHistoryRecordsEveryVersion() throws Exception {
    String url = "jdbc:sqlite:" + tempDir.resolve("history-test.db").toAbsolutePath();
    Flyway flyway = Flyway.configure()
        .dataSource(url, null, null)
        .locations("classpath:db/migration")
        .load();
    flyway.migrate();

    try (Connection connection = DriverManager.getConnection(url);
         Statement statement = connection.createStatement();
         ResultSet resultSet = statement.executeQuery(
             "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank")) {
      List<String> versions = new ArrayList<>();
      while (resultSet.next()) {
        versions.add(resultSet.getString("version"));
        assertThat(resultSet.getBoolean("success")).isTrue();
      }
      assertThat(versions).containsExactly("1", "2", "3");
    }
  }
}
