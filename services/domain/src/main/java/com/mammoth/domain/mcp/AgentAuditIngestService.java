package com.mammoth.domain.mcp;

import com.mammoth.domain.config.DataRootBootstrap;
import com.mammoth.domain.service.AuditService;
import com.mammoth.domain.support.Ids;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writer-side ingestion of the read-only MCP audit journal (ADR-0008 M4 addendum).
 *
 * <p>The MCP process cannot write SQLite, so it appends JSONL. This service reads the journal and
 * inserts one {@code agent_call} row (event id is the idempotency key) plus an append-only
 * {@code audit_log} entry. The writer runs it on a schedule; tests call {@link #ingestNow()}.
 */
@Service
public class AgentAuditIngestService {

  private static final Logger log = LoggerFactory.getLogger(AgentAuditIngestService.class);

  private final DataRootBootstrap bootstrap;
  private final AgentCallRepository agentCallRepository;
  private final AuditService auditService;
  private final TransactionTemplate transactionTemplate;

  public AgentAuditIngestService(DataRootBootstrap bootstrap,
      AgentCallRepository agentCallRepository, AuditService auditService,
      TransactionTemplate transactionTemplate) {
    this.bootstrap = bootstrap;
    this.agentCallRepository = agentCallRepository;
    this.auditService = auditService;
    this.transactionTemplate = transactionTemplate;
  }

  @Scheduled(fixedDelayString = "${mammoth.agent-audit.ingest-interval-ms:30000}",
      initialDelayString = "${mammoth.agent-audit.initial-delay-ms:10000}")
  public void scheduledIngest() {
    try {
      ingestNow();
    } catch (RuntimeException error) {
      log.warn("agent audit ingest failed: {}", error.getMessage());
    }
  }

  /** Ingest all currently visible journal lines; returns the number of new events. */
  public int ingestNow() {
    return transactionTemplate.execute(status -> ingest());
  }

  private int ingest() {
    Path file = bootstrap.root().resolve("audit").resolve(AgentAuditJournal.FILE_NAME);
    if (!Files.isRegularFile(file)) {
      return 0;
    }
    List<String> lines;
    try {
      lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    } catch (Exception error) {
      log.warn("cannot read agent audit journal {}: {}", file, error.getMessage());
      return 0;
    }
    int ingested = 0;
    for (String line : lines) {
      if (line == null || line.isBlank()) {
        continue;
      }
      Map<?, ?> event;
      try {
        Object parsed = McpJson.parse(line);
        if (!(parsed instanceof Map<?, ?> map)) {
          continue;
        }
        event = map;
      } catch (RuntimeException error) {
        log.warn("skipping malformed agent audit line: {}", error.getMessage());
        continue;
      }
      String eventId = asString(event.get("eventId"));
      String agentUserId = asString(event.get("agentUserId"));
      if (eventId == null || agentUserId == null || agentCallRepository.existsById(eventId)) {
        continue;
      }
      String tool = asString(event.get("tool"));
      String projectId = asString(event.get("projectId"));
      int resultCount = asInt(event.get("resultCount"));
      long durationMs = asLong(event.get("durationMs"));
      String error = asString(event.get("error"));
      String at = asString(event.get("at"));
      AgentCall call = new AgentCall(eventId, agentUserId, tool == null ? "unknown" : tool,
          projectId, resultCount, durationMs, error, at == null ? Ids.nowIso() : at,
          Ids.nowIso(), file.toString());
      agentCallRepository.save(call);
      auditService.record(agentUserId, "agent.tool_call", "agent_call", eventId,
          McpJson.write(McpJson.orderedMap(
              "tool", tool,
              "projectId", projectId,
              "resultCount", resultCount,
              "durationMs", durationMs,
              "error", error)));
      ingested++;
    }
    return ingested;
  }

  private static String asString(Object value) {
    return value instanceof String string ? string : null;
  }

  private static int asInt(Object value) {
    return value instanceof Number number ? number.intValue() : 0;
  }

  private static long asLong(Object value) {
    return value instanceof Number number ? number.longValue() : 0L;
  }
}
