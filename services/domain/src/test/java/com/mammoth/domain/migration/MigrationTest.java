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
      "knowledge_source_ref", "knowledge_chunk", "audit_log", "session", "agent_call",
      "work_item", "requirement_evidence", "requirement_assessment", "requirement_snapshot",
      "solution_version", "solution_design_item", "acceptance_criterion", "solution_task",
      "approval_record",
      "project_repository", "project_baseline", "project_baseline_repo", "execution_record",
      "execution_change", "execution_task", "execution_deviation", "delivery_snapshot",
      "knowledge_update_proposal", "external_source", "import_batch", "external_record",
      "notification", "notification_delivery", "server_job");

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
    assertThat(first.migrationsExecuted).isEqualTo(10);
    assertThat(first.targetSchemaVersion).isEqualTo("10");

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
      assertThat(columns(statement, "app_user"))
          .contains("email", "account_type", "password_reset_required");
      assertThat(columns(statement, "session")).contains("password_reset_required");
    }
  }

  @Test
  void v7BackfillsOrgAdminForTheOldestUser() throws Exception {
    String url = "jdbc:sqlite:" + tempDir.resolve("backfill-test.db").toAbsolutePath();
    Flyway.configure()
        .dataSource(url, null, null)
        .locations("classpath:db/migration")
        .target("6")
        .load()
        .migrate();

    try (Connection connection = DriverManager.getConnection(url);
         Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO organization (id, name, created_at)"
          + " VALUES ('org-1', 'Org', '2026-01-01T00:00:00Z')");
      statement.execute("INSERT INTO app_user"
          + " (id, org_id, username, display_name, password_hash, status, created_at)"
          + " VALUES ('user-old', 'org-1', 'old', 'Old', 'hash', 'active',"
          + " '2026-01-01T00:00:00Z')");
      statement.execute("INSERT INTO app_user"
          + " (id, org_id, username, display_name, password_hash, status, created_at)"
          + " VALUES ('user-new', 'org-1', 'new', 'New', 'hash', 'active',"
          + " '2026-01-02T00:00:00Z')");
    }

    Flyway.configure()
        .dataSource(url, null, null)
        .locations("classpath:db/migration")
        .load()
        .migrate();

    try (Connection connection = DriverManager.getConnection(url);
         Statement statement = connection.createStatement();
         ResultSet resultSet = statement.executeQuery(
             "SELECT user_id, role, scope_type FROM role_assignment")) {
      assertThat(resultSet.next()).isTrue();
      assertThat(resultSet.getString("user_id")).isEqualTo("user-old");
      assertThat(resultSet.getString("role")).isEqualTo("org_admin");
      assertThat(resultSet.getString("scope_type")).isEqualTo("organization");
      assertThat(resultSet.next()).isFalse();
    }
  }

  private static List<String> columns(Statement statement, String table) throws Exception {
    List<String> columns = new ArrayList<>();
    try (ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
      while (resultSet.next()) {
        columns.add(resultSet.getString("name"));
      }
    }
    return columns;
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
      assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
    }
  }
}
