package com.mammoth.probe;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Read-only sample endpoints proving the REST surface of the same project. */
@RestController
public class HealthController {

  private final long startedAt = System.currentTimeMillis();

  @GetMapping("/health")
  public Map<String, Object> health() {
    return Map.of(
        "status", "up",
        "runtime", "spring-boot",
        "uptimeMs", System.currentTimeMillis() - startedAt,
        "now", Instant.now().toString());
  }

  @GetMapping("/api/projects/{id}")
  public Map<String, Object> project(@PathVariable String id) {
    return Map.of(
        "id", id,
        "name", "ACME-MES-2026",
        "customer", "ACME",
        "product", "MES");
  }
}
