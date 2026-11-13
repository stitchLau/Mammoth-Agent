package com.mammoth.domain.notification;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Outbox row for an external notification channel (email/WeCom). */
@Entity
@Table(name = "notification_delivery")
public class NotificationDelivery {

  public static final String CHANNEL_IN_APP = "in_app";
  public static final String CHANNEL_EMAIL = "email";
  public static final String CHANNEL_WECOM = "wecom";
  public static final String STATUS_PENDING = "pending";
  public static final String STATUS_SENT = "sent";
  public static final String STATUS_FAILED = "failed";
  public static final String STATUS_DEAD = "dead";
  public static final String STATUS_SKIPPED = "skipped";

  @Id
  private String id;

  private String notificationId;

  private String channel;

  private String status;

  private int attempts;

  private String lastError;

  private String nextAttemptAt;

  private String sentAt;

  private String createdAt;

  private String updatedAt;

  protected NotificationDelivery() {
  }

  public NotificationDelivery(String id, String notificationId, String channel, String status,
      String nextAttemptAt, String createdAt, String updatedAt) {
    this.id = id;
    this.notificationId = notificationId;
    this.channel = channel;
    this.status = status;
    this.nextAttemptAt = nextAttemptAt;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public void markSent(String now) {
    this.status = STATUS_SENT;
    this.sentAt = now;
    this.updatedAt = now;
    this.lastError = null;
    this.nextAttemptAt = null;
  }

  public void markFailed(String error, String nextAttemptAt, String now) {
    this.attempts++;
    this.status = STATUS_FAILED;
    this.lastError = error;
    this.nextAttemptAt = nextAttemptAt;
    this.updatedAt = now;
  }

  public void markDead(String error, String now) {
    this.attempts++;
    this.status = STATUS_DEAD;
    this.lastError = error;
    this.updatedAt = now;
  }

  public void markSkipped(String reason, String now) {
    this.status = STATUS_SKIPPED;
    this.lastError = reason;
    this.updatedAt = now;
  }

  public String getId() {
    return id;
  }

  public String getNotificationId() {
    return notificationId;
  }

  public String getChannel() {
    return channel;
  }

  public String getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public String getLastError() {
    return lastError;
  }

  public String getNextAttemptAt() {
    return nextAttemptAt;
  }

  public String getSentAt() {
    return sentAt;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }
}
