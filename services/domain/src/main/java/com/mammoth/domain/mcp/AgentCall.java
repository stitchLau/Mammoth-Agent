package com.mammoth.domain.mcp;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Durable MCP/agent tool-call audit record (ADR-0008 M4 addendum). MCP writes a local JSONL journal
 * because its SQLite connection is read-only; the writer process ingests the journal into this table
 * (and appends an immutable {@code audit_log} entry).
 */
@Entity
@Table(name = "agent_call")
public class AgentCall {

  @Id
  private String eventId;

  private String agentUserId;

  private String tool;

  private String projectId;

  private int resultCount;

  private long durationMs;

  private String error;

  private String at;

  private String ingestedAt;

  private String sourceFile;

  protected AgentCall() {
  }

  public AgentCall(String eventId, String agentUserId, String tool, String projectId,
      int resultCount, long durationMs, String error, String at, String ingestedAt,
      String sourceFile) {
    this.eventId = eventId;
    this.agentUserId = agentUserId;
    this.tool = tool;
    this.projectId = projectId;
    this.resultCount = resultCount;
    this.durationMs = durationMs;
    this.error = error;
    this.at = at;
    this.ingestedAt = ingestedAt;
    this.sourceFile = sourceFile;
  }

  public String getEventId() {
    return eventId;
  }

  public String getAgentUserId() {
    return agentUserId;
  }

  public String getTool() {
    return tool;
  }

  public String getProjectId() {
    return projectId;
  }

  public int getResultCount() {
    return resultCount;
  }

  public long getDurationMs() {
    return durationMs;
  }

  public String getError() {
    return error;
  }

  public String getAt() {
    return at;
  }

  public String getIngestedAt() {
    return ingestedAt;
  }

  public String getSourceFile() {
    return sourceFile;
  }
}
