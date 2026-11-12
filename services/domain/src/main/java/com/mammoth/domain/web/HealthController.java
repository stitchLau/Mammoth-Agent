package com.mammoth.domain.web;

import com.mammoth.domain.config.DataRootBootstrap;
import com.mammoth.domain.config.DataRootResolver;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Minimal health/meta surface used by the M1 engineering skeleton and its CI smoke checks. */
@RestController
@RequestMapping("/api")
public class HealthController {

  private final DataRootResolver dataRootResolver;
  private final DataRootBootstrap dataRootBootstrap;
  private final JdbcTemplate jdbcTemplate;

  public HealthController(
      DataRootResolver dataRootResolver,
      DataRootBootstrap dataRootBootstrap,
      DataSource dataSource) {
    this.dataRootResolver = dataRootResolver;
    this.dataRootBootstrap = dataRootBootstrap;
    this.jdbcTemplate = new JdbcTemplate(dataSource);
  }

  @GetMapping("/health")
  public Map<String, Object> health() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", "up");
    body.put("service", "mammoth-domain-service");
    body.put("time", Instant.now().toString());
    return body;
  }

  @GetMapping("/meta")
  public Map<String, Object> meta() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("service", "mammoth-domain-service");
    body.put("version", "0.1.0-SNAPSHOT");
    body.put("dataRoot", dataRootResolver.root().toString());
    body.put("database", dataRootBootstrap.databaseFile().toString());
    body.put("schemaVersion", schemaVersion());
    body.put("architecture", "ADR-0001/0002/0003 (Accepted 2026-11-12)");
    return body;
  }

  private String schemaVersion() {
    try {
      var versions = jdbcTemplate.queryForList(
          "SELECT version FROM flyway_schema_history ORDER BY installed_rank", String.class);
      return versions.isEmpty() ? "none" : versions.get(versions.size() - 1);
    } catch (Exception error) {
      return "unavailable: " + error.getClass().getSimpleName();
    }
  }
}
