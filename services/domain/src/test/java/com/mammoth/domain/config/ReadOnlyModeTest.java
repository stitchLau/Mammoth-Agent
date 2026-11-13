package com.mammoth.domain.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * M3 S1 acceptance: the read-only entrypoint (MCP, ADR-0008) starts on an initialized data root
 * without Flyway, without the writer lock, and cannot write.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "mammoth.data-root.read-only=true")
class ReadOnlyModeTest {

  private static final Path DATA_ROOT = initializeRoot();

  private static Path initializeRoot() {
    try {
      Path root = Files.createTempDirectory("mammoth-readonly-mode");
      Files.createDirectories(root.resolve("db"));
      Path database = root.resolve("db").resolve("mammoth.db");
      Flyway.configure()
          .dataSource("jdbc:sqlite:" + database, null, null)
          .locations("classpath:db/migration")
          .load()
          .migrate();
      // Simulate a data root initialized by the writer entrypoint (default organization exists).
      try (Connection connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + database);
           Statement statement = connection.createStatement()) {
        statement.executeUpdate(
            "INSERT INTO organization(id, name, created_at) VALUES('org-default','Test Org','now')");
      }
      return root;
    } catch (Exception error) {
      throw new IllegalStateException("cannot initialize the read-only test data root", error);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("mammoth.data-root", DATA_ROOT::toString);
  }

  @Autowired
  private DataSource dataSource;

  @Autowired
  private ApplicationContext context;

  @Test
  void startsReadOnlyWithoutFlywayAndRejectsWrites() throws Exception {
    assertThat(context.getBeanNamesForType(Flyway.class)).isEmpty();
    assertThat(Files.exists(DATA_ROOT.resolve(".mammoth-instance.lock"))).isFalse();

    try (Connection connection = dataSource.getConnection()) {
      try (Statement statement = connection.createStatement();
           ResultSet resultSet = statement.executeQuery("PRAGMA query_only")) {
        assertThat(resultSet.next()).isTrue();
        assertThat(resultSet.getInt(1)).isEqualTo(1);
      }
      try (Statement statement = connection.createStatement()) {
        assertThatThrownBy(() -> statement.executeUpdate(
            "INSERT INTO audit_log(id, action, object_type, at) VALUES('probe','test','test','now')"))
            .isInstanceOf(SQLException.class);
      }
    }
  }
}
