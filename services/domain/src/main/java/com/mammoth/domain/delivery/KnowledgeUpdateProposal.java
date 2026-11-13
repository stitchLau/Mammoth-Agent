package com.mammoth.domain.delivery;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Proposal to feed delivery knowledge back into the project knowledge base. Accepting a proposal
 * creates a draft knowledge document that still has to pass human review (four-eyes) before it is
 * searchable (docs/06 §7).
 */
@Entity
@Table(name = "knowledge_update_proposal")
public class KnowledgeUpdateProposal {

  public static final String DRAFT = "draft";
  public static final String ACCEPTED = "accepted";
  public static final String REJECTED = "rejected";

  @Id
  private String id;

  private String projectId;

  private String workItemId;

  private String deliverySnapshotId;

  private String title;

  private String bodyMarkdown;

  private String status;

  private String knowledgeDocumentId;

  private String createdBy;

  private String createdAt;

  private String decidedBy;

  private String decidedAt;

  protected KnowledgeUpdateProposal() {
  }

  public KnowledgeUpdateProposal(String id, String projectId, String workItemId,
      String deliverySnapshotId, String title, String bodyMarkdown, String createdBy,
      String createdAt) {
    this.id = id;
    this.projectId = projectId;
    this.workItemId = workItemId;
    this.deliverySnapshotId = deliverySnapshotId;
    this.title = title;
    this.bodyMarkdown = bodyMarkdown;
    this.status = DRAFT;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
  }

  public void accept(String actorId, String knowledgeDocumentId, String decidedAt) {
    this.status = ACCEPTED;
    this.decidedBy = actorId;
    this.decidedAt = decidedAt;
    this.knowledgeDocumentId = knowledgeDocumentId;
  }

  public void reject(String actorId, String decidedAt) {
    this.status = REJECTED;
    this.decidedBy = actorId;
    this.decidedAt = decidedAt;
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

  public String getDeliverySnapshotId() {
    return deliverySnapshotId;
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

  public String getKnowledgeDocumentId() {
    return knowledgeDocumentId;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public String getDecidedBy() {
    return decidedBy;
  }

  public String getDecidedAt() {
    return decidedAt;
  }
}
