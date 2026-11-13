package com.mammoth.domain.execution;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Actual implementation evidence for one repository range (docs/07 §8, ADR-0007): baseline commit →
 * observed commit, diff file list and a diverged flag. Source content is never stored here.
 */
@Entity
@Table(name = "execution_record")
public class ExecutionRecord {

  @Id
  private String id;

  private String projectId;

  private String workItemId;

  private String solutionVersionId;

  private String baselineId;

  private String repositoryId;

  private String fromCommit;

  private String toCommit;

  private boolean diverged;

  private String summary;

  private String testsNote;

  private String recordedBy;

  private String recordedAt;

  protected ExecutionRecord() {
  }

  public ExecutionRecord(String id, String projectId, String workItemId, String solutionVersionId,
      String baselineId, String repositoryId, String fromCommit, String toCommit, boolean diverged,
      String summary, String testsNote, String recordedBy, String recordedAt) {
    this.id = id;
    this.projectId = projectId;
    this.workItemId = workItemId;
    this.solutionVersionId = solutionVersionId;
    this.baselineId = baselineId;
    this.repositoryId = repositoryId;
    this.fromCommit = fromCommit;
    this.toCommit = toCommit;
    this.diverged = diverged;
    this.summary = summary;
    this.testsNote = testsNote;
    this.recordedBy = recordedBy;
    this.recordedAt = recordedAt;
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

  public String getRepositoryId() {
    return repositoryId;
  }

  public String getFromCommit() {
    return fromCommit;
  }

  public String getToCommit() {
    return toCommit;
  }

  public boolean isDiverged() {
    return diverged;
  }

  public String getSummary() {
    return summary;
  }

  public String getTestsNote() {
    return testsNote;
  }

  public String getRecordedBy() {
    return recordedBy;
  }

  public String getRecordedAt() {
    return recordedAt;
  }
}
