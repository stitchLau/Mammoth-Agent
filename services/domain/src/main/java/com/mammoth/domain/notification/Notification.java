package com.mammoth.domain.notification;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Per-user in-app notification (ADR-0013); email/WeCom are separate delivery rows. */
@Entity
@Table(name = "notification")
public class Notification {

  public static final String STATUS_UNREAD = "unread";
  public static final String STATUS_READ = "read";
  public static final String SEVERITY_INFO = "info";
  public static final String SEVERITY_WARNING = "warning";
  public static final String SEVERITY_ERROR = "error";

  @Id
  private String id;

  private String orgId;

  private String recipientUserId;

  private String type;

  private String title;

  private String body;

  private String projectId;

  private String objectType;

  private String objectId;

  private String link;

  private String severity;

  private String status;

  private String dedupeKey;

  private String createdAt;

  private String readAt;

  protected Notification() {
  }

  public Notification(String id, String orgId, String recipientUserId, String type, String title,
      String body, String projectId, String objectType, String objectId, String link,
      String severity, String dedupeKey, String createdAt) {
    this.id = id;
    this.orgId = orgId;
    this.recipientUserId = recipientUserId;
    this.type = type;
    this.title = title;
    this.body = body;
    this.projectId = projectId;
    this.objectType = objectType;
    this.objectId = objectId;
    this.link = link;
    this.severity = severity == null ? SEVERITY_INFO : severity;
    this.status = STATUS_UNREAD;
    this.dedupeKey = dedupeKey;
    this.createdAt = createdAt;
  }

  public void markRead(String readAt) {
    this.status = STATUS_READ;
    this.readAt = readAt;
  }

  public String getId() {
    return id;
  }

  public String getOrgId() {
    return orgId;
  }

  public String getRecipientUserId() {
    return recipientUserId;
  }

  public String getType() {
    return type;
  }

  public String getTitle() {
    return title;
  }

  public String getBody() {
    return body;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getObjectType() {
    return objectType;
  }

  public String getObjectId() {
    return objectId;
  }

  public String getLink() {
    return link;
  }

  public String getSeverity() {
    return severity;
  }

  public String getStatus() {
    return status;
  }

  public String getDedupeKey() {
    return dedupeKey;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public String getReadAt() {
    return readAt;
  }
}
