package com.mammoth.domain.solution;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One version of the unified SolutionModel of a requirement (docs/03 §6, docs/07 §6).
 *
 * <p>Approved versions are immutable: any change creates a new draft version and the previous
 * approved version is kept as {@code superseded}.
 */
@Entity
@Table(name = "solution_version")
public class SolutionVersion {

  public static final String DRAFT = "draft";
  public static final String IN_REVIEW = "in_review";
  public static final String APPROVED = "approved";
  public static final String SUPERSEDED = "superseded";

  @Id
  private String id;

  private String workItemId;

  private int version;

  private String status;

  private String contextSnapshotId;

  private String title;

  private String changeSummary;

  private String createdBy;

  private String submittedAt;

  private String approverUserId;

  private String approvedAt;

  private String createdAt;

  private String updatedAt;

  protected SolutionVersion() {
  }

  public SolutionVersion(String id, String workItemId, int version, String title,
      String changeSummary, String contextSnapshotId, String createdBy, String now) {
    this.id = id;
    this.workItemId = workItemId;
    this.version = version;
    this.status = DRAFT;
    this.title = title;
    this.changeSummary = changeSummary;
    this.contextSnapshotId = contextSnapshotId;
    this.createdBy = createdBy;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void updateDraft(String title, String changeSummary, String now) {
    this.title = title;
    this.changeSummary = changeSummary;
    this.updatedAt = now;
  }

  public void submit(String snapshotId, String now) {
    this.status = IN_REVIEW;
    this.submittedAt = now;
    this.contextSnapshotId = snapshotId;
    this.updatedAt = now;
  }

  public void approve(String approverUserId, String now) {
    this.status = APPROVED;
    this.approverUserId = approverUserId;
    this.approvedAt = now;
    this.updatedAt = now;
  }

  public void requestChanges(String now) {
    this.status = DRAFT;
    this.submittedAt = null;
    this.updatedAt = now;
  }

  public void supersede(String now) {
    this.status = SUPERSEDED;
    this.updatedAt = now;
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

  public String getStatus() {
    return status;
  }

  public String getContextSnapshotId() {
    return contextSnapshotId;
  }

  public String getTitle() {
    return title;
  }

  public String getChangeSummary() {
    return changeSummary;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public String getSubmittedAt() {
    return submittedAt;
  }

  public String getApproverUserId() {
    return approverUserId;
  }

  public String getApprovedAt() {
    return approvedAt;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }
}
