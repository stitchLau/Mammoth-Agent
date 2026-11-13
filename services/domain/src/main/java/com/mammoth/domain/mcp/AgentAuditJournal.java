package com.mammoth.domain.mcp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only local audit journal for the read-only MCP process (ADR-0008 M4 addendum).
 *
 * <p>The MCP process opens SQLite read-only and therefore cannot write {@code audit_log}. It appends
 * one JSON line per tool call to {@code <dataRoot>/audit/agent-audit.jsonl}; the writer process
 * ingests the lines into {@code agent_call} + {@code audit_log}. The journal never contains tool
 * arguments, prompts or document bodies.
 */
public final class AgentAuditJournal {

  public static final String FILE_NAME = "agent-audit.jsonl";

  private final Path auditDir;

  public AgentAuditJournal(Path auditDir) {
    this.auditDir = auditDir;
  }

  public Path file() {
    return auditDir.resolve(FILE_NAME);
  }

  /**
   * Append one event. Returns the generated event id, or {@code null} when the journal is not
   * writable (read-only roots without an audit directory); callers treat that as best-effort audit.
   */
  public String record(String agentUserId, String tool, String projectId, int resultCount,
      long durationMs, String error) {
    String eventId = UUID.randomUUID().toString();
    String at = Instant.now().toString();
    Object payload = McpJson.orderedMap(
        "eventId", eventId,
        "at", at,
        "agentUserId", agentUserId,
        "tool", tool,
        "projectId", projectId,
        "resultCount", resultCount,
        "durationMs", durationMs,
        "error", truncate(error));
    try {
      Path file = file();
      Files.writeString(file, McpJson.write(payload) + "\n", StandardCharsets.UTF_8,
          StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      return eventId;
    } catch (Exception problem) {
      System.err.println("[mammoth-mcp] agent audit journal unavailable: " + problem.getMessage());
      return null;
    }
  }

  private static String truncate(String value) {
    if (value == null) {
      return null;
    }
    return value.length() <= 500 ? value : value.substring(0, 500);
  }
}
