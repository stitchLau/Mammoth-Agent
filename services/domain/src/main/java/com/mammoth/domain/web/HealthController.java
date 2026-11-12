package com.mammoth.domain.web;

import com.mammoth.domain.config.DataRootResolver;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Minimal health/meta surface used by the M1 engineering skeleton and its CI smoke checks. */
@RestController
@RequestMapping("/api")
public class HealthController {

  private final DataRootResolver dataRootResolver;

  public HealthController(DataRootResolver dataRootResolver) {
    this.dataRootResolver = dataRootResolver;
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
    body.put("architecture", "ADR-0001/0002/0003 (Accepted 2026-11-12)");
    return body;
  }
}
