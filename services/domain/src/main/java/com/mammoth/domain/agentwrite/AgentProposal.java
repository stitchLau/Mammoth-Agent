package com.mammoth.domain.agentwrite;

import com.mammoth.domain.support.Ids;
import com.mammoth.domain.web.ApiException;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.Set;

/**
 * ADR-0015 P1: one agent-originated proposal that must be approved by humans before any Domain
 * write. The entity owns the proposal state machine; authorization (who may approve) and the
 * four-eyes distinctness check live in the service because they need the approval log.
 */
@Entity
@Table(name = "agent_proposal")
public class AgentProposal {

  public static final String PROPOSER_AGENT = "agent";

  public static final String RISK_NORMAL = "normal";
  public static final String RISK_HIGH = "high";
  public static final Set<String> RISK_LEVELS = Set.of(RISK_NORMAL, RISK_HIGH);

  public static final String STATUS_PENDING = "pending";
  public static final String STATUS_AWAITING_SECOND = "awaiting_second";
  public static final String STATUS_APPROVED = "approved";
  public static final String STATUS_REJECTED = "rejected";
  public static final String STATUS_APPLYING = "applying";
  public static final String STATUS_APPLIED = "applied";
  public static final String STATUS_FAILED = "failed";
  public static final String STATUS_EXPIRED = "expired";
  public static final Set<String> STATUSES = Set.of(STATUS_PENDING, STATUS_AWAITING_SECOND,
      STATUS_APPROVED, STATUS_REJECTED, STATUS_APPLYING, STATUS_APPLIED, STATUS_FAILED,
      STATUS_EXPIRED);

  @Id
  private String id;

  private String projectId;

  private String proposalType;

  private String payloadJson;

  private String summary;

  private String proposerKind;

  private String proposerUserId;

  private String agentRunRef;

  private String idempotencyKey;

  private String status;

  private String riskLevel;

  private String failureReason;

  private String createdAt;

  private String decidedAt;

  private String appliedAt;

  protected AgentProposal() {
  }

  public AgentProposal(String id, String projectId, String proposalType, String payloadJson,
      String summary, String proposerKind, String proposerUserId, String agentRunRef,
      String idempotencyKey, String riskLevel, String now) {
    this.id = id;
    this.projectId = requireText(projectId, "projectId");
    this.proposalType = requireText(proposalType, "proposalType");
    this.payloadJson = requireText(payloadJson, "payloadJson");
    this.summary = summary;
    this.proposerKind = proposerKind == null || proposerKind.isBlank()
        ? PROPOSER_AGENT : proposerKind.trim().toLowerCase(Locale.ROOT);
    if (!PROPOSER_AGENT.equals(this.proposerKind)) {
      throw ApiException.badRequest("unsupported proposer kind: " + proposerKind);
    }
    this.proposerUserId = proposerUserId;
    this.agentRunRef = agentRunRef;
    this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
    this.status = STATUS_PENDING;
    this.riskLevel = normalizeRisk(riskLevel);
    this.createdAt = now;
  }

  public static String normalizeRisk(String risk) {
    if (risk == null || risk.isBlank()) {
      return RISK_NORMAL;
    }
    String normalized = risk.trim().toLowerCase(Locale.ROOT);
    if (!RISK_LEVELS.contains(normalized)) {
      throw ApiException.badRequest("unsupported risk level: " + risk);
    }
    return normalized;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw ApiException.badRequest(field + " is required");
    }
    return value.trim();
  }

  public boolean isHighRisk() {
    return RISK_HIGH.equals(riskLevel);
  }

  public boolean isOpen() {
    return STATUS_PENDING.equals(status) || STATUS_AWAITING_SECOND.equals(status);
  }

  public boolean isDecided() {
    return STATUS_APPROVED.equals(status) || STATUS_REJECTED.equals(status)
        || STATUS_APPLIED.equals(status) || STATUS_FAILED.equals(status)
        || STATUS_EXPIRED.equals(status);
  }

  /**
   * Record the primary human approval. For high-risk proposals this only advances to
   * {@code awaiting_second}; the service must additionally verify approver identity and roles.
   */
  public void approvePrimary(String approverUserId, String now) {
    requireOpen("approve");
    requireNotSelf(approverUserId);
    if (STATUS_AWAITING_SECOND.equals(status)) {
      throw ApiException.conflict("proposal already has the primary approval");
    }
    this.status = isHighRisk() ? STATUS_AWAITING_SECOND : STATUS_APPROVED;
    this.decidedAt = isHighRisk() ? null : now;
  }

  /** Record the second (high-risk only) approval from a different human. */
  public void approveSecond(String approverUserId, String now) {
    if (!isHighRisk()) {
      throw ApiException.badRequest("second approval is only required for high-risk proposals");
    }
    if (!STATUS_AWAITING_SECOND.equals(status)) {
      throw ApiException.conflict("proposal is not awaiting a second approval");
    }
    requireNotSelf(approverUserId);
    this.status = STATUS_APPROVED;
    this.decidedAt = now;
  }

  public void reject(String reason, String now) {
    requireOpen("reject");
    String trimmed = reason == null ? null : reason.trim();
    if (trimmed == null || trimmed.isEmpty()) {
      throw ApiException.badRequest("a rejection reason is required");
    }
    this.status = STATUS_REJECTED;
    this.failureReason = trimmed;
    this.decidedAt = now;
  }

  /** Mark an approved proposal as being applied by the approval service (idempotent retry). */
  public void markApplying() {
    if (STATUS_APPLIED.equals(status)) {
      return;
    }
    if (STATUS_APPLYING.equals(status)) {
      return;
    }
    if (!STATUS_APPROVED.equals(status)) {
      throw ApiException.conflict("proposal is not approved: " + status);
    }
    this.status = STATUS_APPLYING;
  }

  public void markApplied(String domainObjectId, String now) {
    if (STATUS_APPLIED.equals(status)) {
      return;
    }
    if (!STATUS_APPLYING.equals(status)) {
      throw ApiException.conflict("proposal is not being applied: " + status);
    }
    this.status = STATUS_APPLIED;
    this.appliedAt = now;
    this.failureReason = null;
  }

  public void markFailed(String reason, String now) {
    if (!STATUS_APPLYING.equals(status)) {
      throw ApiException.conflict("proposal is not being applied: " + status);
    }
    this.status = STATUS_FAILED;
    this.failureReason = reason == null || reason.isBlank() ? "apply failed" : reason.trim();
    this.decidedAt = now;
  }

  public void expire(String now) {
    requireOpen("expire");
    this.status = STATUS_EXPIRED;
    this.decidedAt = now;
  }

  private void requireOpen(String action) {
    if (!isOpen()) {
      throw ApiException.conflict("cannot " + action + " a proposal in status " + status);
    }
  }

  private void requireNotSelf(String approverUserId) {
    if (approverUserId == null || approverUserId.isBlank()) {
      throw ApiException.badRequest("approverUserId is required");
    }
    if (proposerUserId != null && proposerUserId.equals(approverUserId)) {
      throw ApiException.forbidden("proposal cannot be approved by its proposer");
    }
  }

  public static String newId() {
    return Ids.newId();
  }

  public String getId() {
    return id;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getProposalType() {
    return proposalType;
  }

  public String getPayloadJson() {
    return payloadJson;
  }

  public String getSummary() {
    return summary;
  }

  public String getProposerKind() {
    return proposerKind;
  }

  public String getProposerUserId() {
    return proposerUserId;
  }

  public String getAgentRunRef() {
    return agentRunRef;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getStatus() {
    return status;
  }

  public String getRiskLevel() {
    return riskLevel;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public String getDecidedAt() {
    return decidedAt;
  }

  public String getAppliedAt() {
    return appliedAt;
  }
}
