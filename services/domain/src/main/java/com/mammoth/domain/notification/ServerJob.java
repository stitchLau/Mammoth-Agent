package com.mammoth.domain.notification;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Durable writer-only job (ADR-0013). SQLite single-writer claim; M5 moves to multi-instance locks. */
@Entity
@Table(name = "server_job")
public class ServerJob {

  public static final String STATUS_QUEUED = "queued";
  public static final String STATUS_RUNNING = "running";
  public static final String STATUS_SUCCEEDED = "succeeded";
  public static final String STATUS_FAILED = "failed";
  public static final String STATUS_DEAD = "dead";
  public static final String TYPE_NOTIFICATION_DISPATCH = "notification.dispatch";

  @Id
  private String id;

  private String type;

  private String payloadJson;

  private String status;

  private int attempts;

  private String nextRunAt;

  private String lockedBy;

  private String lockedAt;

  private String idempotencyKey;

  private String createdBy;

  private String createdAt;

  private String updatedAt;

  private String lastError;

  protected ServerJob() {
  }

  public ServerJob(String id, String type, String payloadJson, String idempotencyKey,
      String createdBy, String createdAt) {
    this.id = id;
    this.type = type;
    this.payloadJson = payloadJson;
    this.status = STATUS_QUEUED;
    this.idempotencyKey = idempotencyKey;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedAt = createdAt;
    this.nextRunAt = createdAt;
  }

  public void markRunning(String worker, String now) {
    this.status = STATUS_RUNNING;
    this.lockedBy = worker;
    this.lockedAt = now;
    this.updatedAt = now;
  }

  public void markSucceeded(String now) {
    this.status = STATUS_SUCCEEDED;
    this.lockedBy = null;
    this.lockedAt = null;
    this.updatedAt = now;
  }

  public void markRetry(String error, String nextRunAt, String now) {
    this.attempts++;
    this.status = STATUS_QUEUED;
    this.lastError = error;
    this.nextRunAt = nextRunAt;
    this.lockedBy = null;
    this.lockedAt = null;
    this.updatedAt = now;
  }

  public void markDead(String error, String now) {
    this.attempts++;
    this.status = STATUS_DEAD;
    this.lastError = error;
    this.lockedBy = null;
    this.lockedAt = null;
    this.updatedAt = now;
  }

  public void requeue(String now) {
    this.status = STATUS_QUEUED;
    this.nextRunAt = now;
    this.updatedAt = now;
  }

  public String getId() {
    return id;
  }

  public String getType() {
    return type;
  }

  public String getPayloadJson() {
    return payloadJson;
  }

  public String getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public String getNextRunAt() {
    return nextRunAt;
  }

  public String getLockedBy() {
    return lockedBy;
  }

  public String getLockedAt() {
    return lockedAt;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }

  public String getLastError() {
    return lastError;
  }
}
