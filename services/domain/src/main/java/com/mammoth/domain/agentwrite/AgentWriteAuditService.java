package com.mammoth.domain.agentwrite;

import com.mammoth.domain.support.Ids;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR-0015 P1: writes the append-only controlled-write audit chain.
 *
 * <p>Business-tx entries use {@link #append} (MANDATORY, joins the caller transaction so the audit
 * and the proposal state change commit atomically). Denials that must survive an API error use
 * {@link #appendIndependent} (REQUIRES_NEW).
 */
@Service
public class AgentWriteAuditService {

  private final AgentWriteAuditRepository auditRepository;

  public AgentWriteAuditService(AgentWriteAuditRepository auditRepository) {
    this.auditRepository = auditRepository;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void append(String proposalId, String stage, String actorKind, String actorUserId,
      String outcome, String requestFingerprint, String domainObjectId) {
    doAppend(proposalId, stage, actorKind, actorUserId, outcome, requestFingerprint,
        domainObjectId);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void appendIndependent(String proposalId, String stage, String actorKind,
      String actorUserId, String outcome, String requestFingerprint, String domainObjectId) {
    doAppend(proposalId, stage, actorKind, actorUserId, outcome, requestFingerprint,
        domainObjectId);
  }

  private void doAppend(String proposalId, String stage, String actorKind, String actorUserId,
      String outcome, String requestFingerprint, String domainObjectId) {
    Optional<AgentWriteAudit> previous =
        auditRepository.findFirstByProposalIdOrderBySeqDesc(proposalId);
    int seq = previous.map(row -> row.getSeq() + 1).orElse(1);
    String prevHash = previous.map(AgentWriteAudit::getEntryHash).orElse(null);
    String at = Ids.nowIso();
    String entryHash = AuditHash.entryHash(prevHash, proposalId, stage, actorKind, actorUserId,
        outcome, at);
    auditRepository.save(new AgentWriteAudit(Ids.newId(), proposalId, seq, stage, actorKind,
        actorUserId, outcome, requestFingerprint, domainObjectId, prevHash, entryHash, at));
  }
}
