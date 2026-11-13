package com.mammoth.domain.baseline;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Immutable project code baseline: one commit per registered repository at capture time (ADR-0007).
 * Baselines are never updated; a new baseline supersedes the previous one by recency.
 */
@Entity
@Table(name = "project_baseline")
public class ProjectBaseline {

  @Id
  private String id;

  private String projectId;

  private String note;

  private boolean complete;

  private String capturedBy;

  private String capturedAt;

  protected ProjectBaseline() {
  }

  public ProjectBaseline(String id, String projectId, String note, boolean complete,
      String capturedBy, String capturedAt) {
    this.id = id;
    this.projectId = projectId;
    this.note = note;
    this.complete = complete;
    this.capturedBy = capturedBy;
    this.capturedAt = capturedAt;
  }

  public String getId() {
    return id;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getNote() {
    return note;
  }

  public boolean isComplete() {
    return complete;
  }

  public String getCapturedBy() {
    return capturedBy;
  }

  public String getCapturedAt() {
    return capturedAt;
  }
}
