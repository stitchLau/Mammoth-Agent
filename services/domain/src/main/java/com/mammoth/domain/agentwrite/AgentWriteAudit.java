package com.mammoth.domain.agentwrite;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * ADR-0015 P1: append-only audit for every propose/approve/reject/apply/deny step. Rows form an
 * application-level hash chain ({@code prevHash} → {@code entryHash}) so tampering is detectable.
 */
@Entity
@Table(name = "agent_write_audit")
public class AgentWriteAudit {

  public static final String STAGE_PROPOSE = "propose";
  public static final String STAGE_APPROVE = "approve";
  public static final String STAGE_REJECT = "reject";
  public static final String STAGE_APPLY = "apply";
  public static final String STAGE_DENY = "deny";

  @Id
  private String id;

  private String proposalId;

  private String stage;

  private String actorKind;

  private String actorUserId;

  private String outcome;

  private String requestFingerprint;

  private String domainObjectId;

  private String prevHash;

  private String entryHash;

  private String at;

  protected AgentWriteAudit() {
  }

  public AgentWriteAudit(String id, String proposalId, String stage, String actorKind,
      String actorUserId, String outcome, String requestFingerprint, String domainObjectId,
      String prevHash, String entryHash, String now) {
    this.id = id;
    this.proposalId = proposalId;
    this.stage = stage;
    this.actorKind = actorKind;
    this.actorUserId = actorUserId;
    this.outcome = outcome;
    this.requestFingerprint = requestFingerprint;
    this.domainObjectId = domainObjectId;
    this.prevHash = prevHash;
    this.entryHash = entryHash;
    this.at = now;
  }

  public String getId() {
    return id;
  }

  public String getProposalId() {
    return proposalId;
  }

  public String getStage() {
    return stage;
  }

  public String getActorKind() {
    return actorKind;
  }

  public String getActorUserId() {
    return actorUserId;
  }

  public String getOutcome() {
    return outcome;
  }

  public String getRequestFingerprint() {
    return requestFingerprint;
  }

  public String getDomainObjectId() {
    return domainObjectId;
  }

  public String getPrevHash() {
    return prevHash;
  }

  public String getEntryHash() {
    return entryHash;
  }

  public String getAt() {
    return at;
  }
}
