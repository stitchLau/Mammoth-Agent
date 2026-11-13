package com.mammoth.domain.external;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One imported external record/version; a human may map it to requirement evidence. */
@Entity
@Table(name = "external_record")
public class ExternalRecord {

  public static final String STATUS_UNMAPPED = "unmapped";
  public static final String STATUS_MAPPED = "mapped";
  public static final String STATUS_REJECTED = "rejected";

  @Id
  private String id;

  private String batchId;

  private String sourceId;

  private String projectId;

  private String externalType;

  private String externalId;

  private String externalKey;

  private String externalVersion;

  private String title;

  private String description;

  private String sourceUrlSanitized;

  private String contentHash;

  private String mappedWorkItemId;

  private String mappedEvidenceId;

  private String mappedCriterionId;

  private String status;

  private String note;

  private String metadataJson;

  private String createdAt;

  protected ExternalRecord() {
  }

  public ExternalRecord(String id, String batchId, String sourceId, String projectId,
      String externalType, String externalId, String externalKey, String externalVersion,
      String title, String description, String sourceUrlSanitized, String contentHash,
      String metadataJson, String createdAt) {
    this.id = id;
    this.batchId = batchId;
    this.sourceId = sourceId;
    this.projectId = projectId;
    this.externalType = externalType;
    this.externalId = externalId;
    this.externalKey = externalKey;
    this.externalVersion = externalVersion;
    this.title = title;
    this.description = description;
    this.sourceUrlSanitized = sourceUrlSanitized;
    this.contentHash = contentHash;
    this.metadataJson = metadataJson;
    this.status = STATUS_UNMAPPED;
    this.createdAt = createdAt;
  }

  public void map(String workItemId, String evidenceId, String criterionId) {
    this.mappedWorkItemId = workItemId;
    this.mappedEvidenceId = evidenceId;
    this.mappedCriterionId = criterionId;
    this.status = STATUS_MAPPED;
  }

  public void reject(String note) {
    this.note = note;
    this.status = STATUS_REJECTED;
  }

  public String getId() {
    return id;
  }

  public String getBatchId() {
    return batchId;
  }

  public String getSourceId() {
    return sourceId;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getExternalType() {
    return externalType;
  }

  public String getExternalId() {
    return externalId;
  }

  public String getExternalKey() {
    return externalKey;
  }

  public String getExternalVersion() {
    return externalVersion;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public String getSourceUrlSanitized() {
    return sourceUrlSanitized;
  }

  public String getContentHash() {
    return contentHash;
  }

  public String getMappedWorkItemId() {
    return mappedWorkItemId;
  }

  public String getMappedEvidenceId() {
    return mappedEvidenceId;
  }

  public String getMappedCriterionId() {
    return mappedCriterionId;
  }

  public String getStatus() {
    return status;
  }

  public String getNote() {
    return note;
  }

  public String getMetadataJson() {
    return metadataJson;
  }

  public String getCreatedAt() {
    return createdAt;
  }
}
