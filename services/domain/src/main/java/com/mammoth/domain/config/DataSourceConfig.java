package com.mammoth.domain.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/**
 * SQLite datasource (ADR-0003 M1 primary database) rooted in the validated Mammoth data directory.
 * The writer entrypoint runs Flyway on startup; read-only entrypoints (MCP, ADR-0008) never migrate
 * and open the database read-only.
 *
 * <p>WAL mode, foreign keys and a busy timeout are set on writer connections; business code must not
 * depend on SQLite-specific SQL outside migration/search adapters.
 */
@Configuration
public class DataSourceConfig {

  /** Latest migration version this build understands (ADR-0008 read-only startup check). */
  static final int MIN_SCHEMA_VERSION = 8;

  private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

  @Bean
  public DataSource dataSource(DataRootBootstrap bootstrap,
      @Value("${mammoth.data-root.read-only:false}") boolean readOnly) {
    Path databaseFile = bootstrap.databaseFile();
    SQLiteConfig config = new SQLiteConfig();
    config.setBusyTimeout(5_000);
    if (readOnly) {
      if (!Files.isRegularFile(databaseFile)) {
        throw new IllegalStateException(
            "Mammoth database not found (" + databaseFile + "). Read-only mode does not create"
                + " data; start the web/management entrypoint once to initialize it.");
      }
      config.setReadOnly(true);
      SQLiteDataSource dataSource = new ReadOnlySqliteDataSource(config);
      dataSource.setUrl("jdbc:sqlite:" + databaseFile.toAbsolutePath());
      assertSchemaReady(dataSource, databaseFile);
      log.info("SQLite datasource (read-only): {}", databaseFile.toAbsolutePath());
      return dataSource;
    }
    try {
      Files.createDirectories(databaseFile.getParent());
    } catch (Exception error) {
      throw new IllegalStateException(
          "cannot create database directory: " + databaseFile.getParent(), error);
    }
    config.setJournalMode(SQLiteConfig.JournalMode.WAL);
    config.enforceForeignKeys(true);
    SQLiteDataSource dataSource = new SQLiteDataSource(config);
    dataSource.setUrl("jdbc:sqlite:" + databaseFile.toAbsolutePath());
    log.info("SQLite datasource: {}", databaseFile.toAbsolutePath());
    return dataSource;
  }

  /**
   * Explicit Flyway bean (ADR-0003).
   *
   * <p>Spring Boot 4 ships Flyway auto-configuration in a separate module, and this project pins
   * Flyway to 13.12.1 (the version validated for SQLite in S4). Declaring the bean keeps migration
   * execution explicit, deterministic and version-pinned: it runs during context startup, before
   * the HTTP server accepts requests.
   *
   * <p>Read-only entrypoints (MCP, ADR-0008) do not create this bean and never migrate.
   */
  @Bean
  @ConditionalOnProperty(
      name = "mammoth.data-root.read-only", havingValue = "false", matchIfMissing = true)
  public Flyway flyway(DataSource dataSource) {
    Flyway flyway = Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .baselineOnMigrate(false)
        .load();
    var result = flyway.migrate();
    log.info(
        "Flyway schema up to date: executed={}, targetVersion={}",
        result.migrationsExecuted,
        result.targetSchemaVersion);
    return flyway;
  }

  /**
   * Read-only startup gate (ADR-0008): fail with an actionable message instead of runtime SQL
   * errors when the data root was never initialized by the writer entrypoint.
   */
  static void assertSchemaReady(DataSource dataSource, Path databaseFile) {
    try (Connection connection = dataSource.getConnection();
         Statement statement = connection.createStatement();
         ResultSet resultSet = statement.executeQuery(
             "SELECT MAX(CAST(version AS INTEGER)) FROM flyway_schema_history WHERE success = 1")) {
      int version = resultSet.next() ? resultSet.getInt(1) : 0;
      if (version < MIN_SCHEMA_VERSION) {
        throw new IllegalStateException(
            "Mammoth data root schema is v" + version + " (" + databaseFile
                + ") but the read-only entrypoint requires v" + MIN_SCHEMA_VERSION
                + " or newer. Start the web/management entrypoint once to run migrations.");
      }
    } catch (SQLException error) {
      throw new IllegalStateException(
          "Mammoth data root is not initialized (" + databaseFile + "): no Flyway schema history."
              + " Start the web/management entrypoint once to run migrations, then retry the"
              + " read-only process.", error);
    }
  }

  /** SQLite datasource that additionally forces {@code PRAGMA query_only=ON} on every connection. */
  static final class ReadOnlySqliteDataSource extends SQLiteDataSource {

    ReadOnlySqliteDataSource(SQLiteConfig config) {
      super(config);
    }

    @Override
    public Connection getConnection() throws SQLException {
      return guard(super.getConnection());
    }

    private static Connection guard(Connection connection) throws SQLException {
      try (Statement statement = connection.createStatement()) {
        statement.execute("PRAGMA query_only=ON");
      } catch (SQLException error) {
        connection.close();
        throw error;
      }
      return connection;
    }
  }
}
