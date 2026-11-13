package com.mammoth.domain.delivery;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * As-built delivery snapshot (docs/07 §8): generated from the approved solution plus implementation
 * evidence, then approved by a second person. Approved snapshots are immutable; changes require a
 * new snapshot.
 */
@Entity
@Table(name = "delivery_snapshot")
public class DeliverySnapshot {

  public static final String DRAFT = "draft";
  public static final String APPROVED = "approved";

  @Id
  private String id;

  private String projectId;

  private String workItemId;

  private String solutionVersionId;

  private String baselineId;

  private String title;

  private String bodyMarkdown;

  private String status;

  private String artifactId;

  private String createdBy;

  private String createdAt;

  private String approvedBy;

  private String approvedAt;

  protected DeliverySnapshot() {
  }

  public DeliverySnapshot(String id, String projectId, String workItemId, String solutionVersionId,
      String baselineId, String title, String bodyMarkdown, String createdBy, String createdAt) {
    this.id = id;
    this.projectId = projectId;
    this.workItemId = workItemId;
    this.solutionVersionId = solutionVersionId;
    this.baselineId = baselineId;
    this.title = title;
    this.bodyMarkdown = bodyMarkdown;
    this.status = DRAFT;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
  }

  public void approve(String approverId, String artifactId, String approvedAt) {
    this.status = APPROVED;
    this.approvedBy = approverId;
    this.approvedAt = approvedAt;
    this.artifactId = artifactId;
  }

  public String getId() {
    return id;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getWorkItemId() {
    return workItemId;
  }

  public String getSolutionVersionId() {
    return solutionVersionId;
  }

  public String getBaselineId() {
    return baselineId;
  }

  public String getTitle() {
    return title;
  }

  public String getBodyMarkdown() {
    return bodyMarkdown;
  }

  public String getStatus() {
    return status;
  }

  public String getArtifactId() {
    return artifactId;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public String getApprovedBy() {
    return approvedBy;
  }

  public String getApprovedAt() {
    return approvedAt;
  }
}
