package com.mammoth.domain.agentwrite;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** ADR-0015 P1: one human decision on a proposal (primary, or the high-risk second reviewer). */
@Entity
@Table(name = "agent_proposal_approval")
public class AgentProposalApproval {

  public static final String DECISION_APPROVE = "approve";
  public static final String DECISION_REJECT = "reject";

  public static final String ROLE_PRIMARY = "primary";
  public static final String ROLE_SECOND = "second";

  @Id
  private String id;

  private String proposalId;

  private String approverUserId;

  private String decision;

  private String reason;

  private String reviewerRole;

  private String humanSessionRef;

  private String decidedAt;

  protected AgentProposalApproval() {
  }

  public AgentProposalApproval(String id, String proposalId, String approverUserId,
      String decision, String reason, String reviewerRole, String humanSessionRef, String now) {
    this.id = id;
    this.proposalId = proposalId;
    this.approverUserId = approverUserId;
    this.decision = decision;
    this.reason = reason;
    this.reviewerRole = reviewerRole;
    this.humanSessionRef = humanSessionRef;
    this.decidedAt = now;
  }

  public String getId() {
    return id;
  }

  public String getProposalId() {
    return proposalId;
  }

  public String getApproverUserId() {
    return approverUserId;
  }

  public String getDecision() {
    return decision;
  }

  public String getReason() {
    return reason;
  }

  public String getReviewerRole() {
    return reviewerRole;
  }

  public String getHumanSessionRef() {
    return humanSessionRef;
  }

  public String getDecidedAt() {
    return decidedAt;
  }
}
