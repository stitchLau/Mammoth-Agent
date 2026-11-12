package com.mammoth.domain.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Append-only audit record (ADR-0006). Never updated or deleted by application code. */
@Entity
@Table(name = "audit_log")
public class AuditLog {

  @Id
  private String id;

  private String actorUserId;

  private String action;

  private String objectType;

  private String objectId;

  private String at;

  private String detailJson;

  protected AuditLog() {
  }

  public AuditLog(String id, String actorUserId, String action, String objectType, String objectId,
      String at, String detailJson) {
    this.id = id;
    this.actorUserId = actorUserId;
    this.action = action;
    this.objectType = objectType;
    this.objectId = objectId;
    this.at = at;
    this.detailJson = detailJson;
  }

  public String getId() {
    return id;
  }

  public String getActorUserId() {
    return actorUserId;
  }

  public String getAction() {
    return action;
  }

  public String getObjectType() {
    return objectType;
  }

  public String getObjectId() {
    return objectId;
  }

  public String getAt() {
    return at;
  }

  public String getDetailJson() {
    return detailJson;
  }
}
