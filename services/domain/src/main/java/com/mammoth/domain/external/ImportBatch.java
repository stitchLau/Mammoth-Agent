package com.mammoth.domain.external;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One applied import batch: original Artifact + mapping + counts (ADR-0011). */
@Entity
@Table(name = "import_batch")
public class ImportBatch {

  public static final String STATUS_APPLIED = "applied";
  public static final String STATUS_FAILED = "failed";

  @Id
  private String id;

  private String projectId;

  private String sourceId;

  private String artifactId;

  private String fileSha256;

  private String mappingProfile;

  private String status;

  private int rowCount;

  private int acceptedCount;

  private int rejectedCount;

  private String errorSummary;

  private String createdBy;

  private String createdAt;

  private String finishedAt;

  protected ImportBatch() {
  }

  public ImportBatch(String id, String projectId, String sourceId, String artifactId,
      String fileSha256, String mappingProfile, String status, int rowCount, int acceptedCount,
      int rejectedCount, String errorSummary, String createdBy, String createdAt, String finishedAt) {
    this.id = id;
    this.projectId = projectId;
    this.sourceId = sourceId;
    this.artifactId = artifactId;
    this.fileSha256 = fileSha256;
    this.mappingProfile = mappingProfile;
    this.status = status;
    this.rowCount = rowCount;
    this.acceptedCount = acceptedCount;
    this.rejectedCount = rejectedCount;
    this.errorSummary = errorSummary;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.finishedAt = finishedAt;
  }

  public String getId() {
    return id;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getSourceId() {
    return sourceId;
  }

  public String getArtifactId() {
    return artifactId;
  }

  public String getFileSha256() {
    return fileSha256;
  }

  public String getMappingProfile() {
    return mappingProfile;
  }

  public String getStatus() {
    return status;
  }

  public int getRowCount() {
    return rowCount;
  }

  public int getAcceptedCount() {
    return acceptedCount;
  }

  public int getRejectedCount() {
    return rejectedCount;
  }

  public String getErrorSummary() {
    return errorSummary;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public String getFinishedAt() {
    return finishedAt;
  }
}
