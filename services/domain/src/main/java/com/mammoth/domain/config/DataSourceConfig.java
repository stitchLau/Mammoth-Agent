package com.mammoth.domain.config;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/**
 * SQLite datasource (ADR-0003 M1 primary database) rooted in the validated Mammoth data directory.
 * Flyway runs on startup against this datasource (Boot auto-configuration).
 *
 * <p>WAL mode, foreign keys and a busy timeout are set on every connection; business code must not
 * depend on SQLite-specific SQL outside migration/search adapters.
 */
@Configuration
public class DataSourceConfig {

  private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

  @Bean
  public DataSource dataSource(DataRootBootstrap bootstrap) {
    Path databaseFile = bootstrap.databaseFile();
    try {
      Files.createDirectories(databaseFile.getParent());
    } catch (Exception error) {
      throw new IllegalStateException("cannot create database directory: " + databaseFile.getParent(), error);
    }
    SQLiteConfig config = new SQLiteConfig();
    config.setJournalMode(SQLiteConfig.JournalMode.WAL);
    config.enforceForeignKeys(true);
    config.setBusyTimeout(5_000);
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
   */
  @Bean
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
}
