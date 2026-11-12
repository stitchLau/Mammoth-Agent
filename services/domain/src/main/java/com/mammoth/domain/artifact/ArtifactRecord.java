package com.mammoth.domain.artifact;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Artifact metadata; the bytes live in the content-addressed store (ADR-0004). */
@Entity
@Table(name = "artifact")
public class ArtifactRecord {

  @Id
  private String id;

  private String projectId;

  private String kind;

  private String originalName;

  private String contentHash;

  private String storagePath;

  private long sizeBytes;

  private String mime;

  private String sourceNote;

  private String uploadedBy;

  private String createdAt;

  protected ArtifactRecord() {
  }

  public ArtifactRecord(String id, String projectId, String kind, String originalName,
      String contentHash, String storagePath, long sizeBytes, String mime, String sourceNote,
      String uploadedBy, String createdAt) {
    this.id = id;
    this.projectId = projectId;
    this.kind = kind;
    this.originalName = originalName;
    this.contentHash = contentHash;
    this.storagePath = storagePath;
    this.sizeBytes = sizeBytes;
    this.mime = mime;
    this.sourceNote = sourceNote;
    this.uploadedBy = uploadedBy;
    this.createdAt = createdAt;
  }

  public String getId() {
    return id;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getKind() {
    return kind;
  }

  public String getOriginalName() {
    return originalName;
  }

  public String getContentHash() {
    return contentHash;
  }

  public String getStoragePath() {
    return storagePath;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public String getMime() {
    return mime;
  }

  public String getSourceNote() {
    return sourceNote;
  }

  public String getUploadedBy() {
    return uploadedBy;
  }

  public String getCreatedAt() {
    return createdAt;
  }
}
