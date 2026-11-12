package com.mammoth.domain.solution;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Business-level approval/decision record (docs/03 §1). Complements {@code audit_log}: audit proves
 * that something happened, this records what was decided, by whom and with which comment.
 */
@Entity
@Table(name = "approval_record")
public class ApprovalRecord {

  public static final String OBJECT_SOLUTION_VERSION = "solution_version";
  public static final String ACTION_SUBMIT = "submit";
  public static final String ACTION_APPROVE = "approve";
  public static final String ACTION_REQUEST_CHANGES = "request_changes";

  @Id
  private String id;

  private String objectType;

  private String objectId;

  private String action;

  private String actorUserId;

  private String comment;

  private String at;

  protected ApprovalRecord() {
  }

  public ApprovalRecord(String id, String objectType, String objectId, String action,
      String actorUserId, String comment, String at) {
    this.id = id;
    this.objectType = objectType;
    this.objectId = objectId;
    this.action = action;
    this.actorUserId = actorUserId;
    this.comment = comment;
    this.at = at;
  }

  public String getId() {
    return id;
  }

  public String getObjectType() {
    return objectType;
  }

  public String getObjectId() {
    return objectId;
  }

  public String getAction() {
    return action;
  }

  public String getActorUserId() {
    return actorUserId;
  }

  public String getComment() {
    return comment;
  }

  public String getAt() {
    return at;
  }
}
