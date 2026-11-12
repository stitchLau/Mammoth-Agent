package com.mammoth.domain.workitem;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Immutable context baseline of a requirement (ADR-0010). The JSON payload stores identifiers and
 * assessment states only — never copies of customer evidence bodies.
 */
@Entity
@Table(name = "requirement_snapshot")
public class RequirementSnapshot {

  @Id
  private String id;

  private String workItemId;

  private int version;

  private String payloadJson;

  private String createdBy;

  private String createdAt;

  protected RequirementSnapshot() {
  }

  public RequirementSnapshot(String id, String workItemId, int version, String payloadJson,
      String createdBy, String createdAt) {
    this.id = id;
    this.workItemId = workItemId;
    this.version = version;
    this.payloadJson = payloadJson;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
  }

  public String getId() {
    return id;
  }

  public String getWorkItemId() {
    return workItemId;
  }

  public int getVersion() {
    return version;
  }

  public String getPayloadJson() {
    return payloadJson;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public String getCreatedAt() {
    return createdAt;
  }
}
