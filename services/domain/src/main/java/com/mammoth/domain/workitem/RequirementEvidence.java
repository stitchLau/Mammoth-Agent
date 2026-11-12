package com.mammoth.domain.workitem;

import com.mammoth.domain.web.ApiException;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.Set;

/**
 * One immutable evidence batch of a requirement (docs/07 §3). There is deliberately no update or
 * delete operation: raw customer input must stay recoverable and cannot be rewritten by analysis.
 */
@Entity
@Table(name = "requirement_evidence")
public class RequirementEvidence {

  public static final String TYPE_NOTE = "note";
  public static final String TYPE_CONVERSATION = "conversation";
  public static final String TYPE_MEETING = "meeting";
  public static final String TYPE_EMAIL = "email";
  public static final String TYPE_ARTIFACT = "artifact";
  public static final Set<String> TYPES =
      Set.of(TYPE_NOTE, TYPE_CONVERSATION, TYPE_MEETING, TYPE_EMAIL, TYPE_ARTIFACT);

  @Id
  private String id;

  private String workItemId;

  private int batchNo;

  private String evidenceType;

  private String text;

  private String artifactId;

  private String locator;

  private String capturedAt;

  private String addedBy;

  private String createdAt;

  protected RequirementEvidence() {
  }

  public RequirementEvidence(String id, String workItemId, int batchNo, String evidenceType,
      String text, String artifactId, String locator, String capturedAt, String addedBy,
      String now) {
    this.id = id;
    this.workItemId = workItemId;
    this.batchNo = batchNo;
    this.evidenceType = normalizeType(evidenceType);
    this.text = text;
    this.artifactId = artifactId;
    this.locator = locator;
    this.capturedAt = capturedAt == null || capturedAt.isBlank() ? now : capturedAt;
    this.addedBy = addedBy;
    this.createdAt = now;
  }

  public static String normalizeType(String evidenceType) {
    if (evidenceType == null || evidenceType.isBlank()) {
      return TYPE_NOTE;
    }
    String normalized = evidenceType.trim().toLowerCase(Locale.ROOT);
    if (!TYPES.contains(normalized)) {
      throw ApiException.badRequest("unsupported evidence type: " + evidenceType);
    }
    return normalized;
  }

  public String getId() {
    return id;
  }

  public String getWorkItemId() {
    return workItemId;
  }

  public int getBatchNo() {
    return batchNo;
  }

  public String getEvidenceType() {
    return evidenceType;
  }

  public String getText() {
    return text;
  }

  public String getArtifactId() {
    return artifactId;
  }

  public String getLocator() {
    return locator;
  }

  public String getCapturedAt() {
    return capturedAt;
  }

  public String getAddedBy() {
    return addedBy;
  }

  public String getCreatedAt() {
    return createdAt;
  }
}
