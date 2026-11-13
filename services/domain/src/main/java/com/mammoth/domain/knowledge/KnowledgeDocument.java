package com.mammoth.domain.knowledge;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Knowledge document with the M1 lifecycle draft -> needs_review -> verified (ADR-0005). */
@Entity
@Table(name = "knowledge_document")
public class KnowledgeDocument {

  public static final String DRAFT = "draft";
  public static final String NEEDS_REVIEW = "needs_review";
  public static final String VERIFIED = "verified";

  @Id
  private String id;

  private String scopeType;

  private String scopeId;

  private String docType;

  private String title;

  private String body;

  private String status;

  private int version;

  private String createdBy;

  private String reviewer;

  private String reviewedAt;

  /** Code baseline this knowledge was derived from (ADR-0007); null for ordinary knowledge. */
  private String applicableBaselineId;

  private String createdAt;

  private String updatedAt;

  protected KnowledgeDocument() {
  }

  public KnowledgeDocument(String id, String scopeType, String scopeId, String docType, String title,
      String body, String status, int version, String createdBy, String createdAt) {
    this.id = id;
    this.scopeType = scopeType;
    this.scopeId = scopeId;
    this.docType = docType;
    this.title = title;
    this.body = body;
    this.status = status;
    this.version = version;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedAt = createdAt;
  }

  public String getId() {
    return id;
  }

  public String getScopeType() {
    return scopeType;
  }

  public String getScopeId() {
    return scopeId;
  }

  public String getDocType() {
    return docType;
  }

  public String getTitle() {
    return title;
  }

  public String getBody() {
    return body;
  }

  public String getStatus() {
    return status;
  }

  public int getVersion() {
    return version;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public String getReviewer() {
    return reviewer;
  }

  public String getReviewedAt() {
    return reviewedAt;
  }

  public String getApplicableBaselineId() {
    return applicableBaselineId;
  }

  /** Attach the knowledge to a code baseline (delivery loop); null means "not baseline-specific". */
  public void attachBaseline(String baselineId) {
    this.applicableBaselineId = baselineId;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }

  public void submitForReview() {
    this.status = NEEDS_REVIEW;
    this.updatedAt = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
  }

  public void verify(String reviewerId, String reviewedAt) {
    this.status = VERIFIED;
    this.reviewer = reviewerId;
    this.reviewedAt = reviewedAt;
    this.updatedAt = reviewedAt;
  }

  public void revise(String title, String body, String updatedAt) {
    this.title = title;
    this.body = body;
    this.status = DRAFT;
    this.updatedAt = updatedAt;
  }

  /** Edit a draft in place (UI-SPEC P06); status stays draft until it is submitted for review. */
  public void updateDraft(String docType, String title, String body, String updatedAt) {
    this.docType = docType;
    this.title = title;
    this.body = body;
    this.updatedAt = updatedAt;
  }
}
