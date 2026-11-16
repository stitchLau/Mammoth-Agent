package com.mammoth.domain.agentwrite;

import com.mammoth.domain.support.Ids;
import com.mammoth.domain.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR-0015 P1: transaction boundaries for the apply phase. Each method commits its own state so a
 * Domain write failure (or a crash) always leaves an observable proposal state plus audit evidence,
 * instead of rolling the attempt back to "approved" with no trace.
 */
@Service
public class AgentProposalStateService {

  private final AgentProposalRepository proposalRepository;
  private final AgentWriteAuditService auditService;

  public AgentProposalStateService(AgentProposalRepository proposalRepository,
      AgentWriteAuditService auditService) {
    this.proposalRepository = proposalRepository;
    this.auditService = auditService;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markApplying(String proposalId) {
    AgentProposal proposal = requireProposal(proposalId);
    if (AgentProposal.STATUS_APPLYING.equals(proposal.getStatus())
        || AgentProposal.STATUS_APPLIED.equals(proposal.getStatus())) {
      return;
    }
    proposal.markApplying();
    proposalRepository.save(proposal);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void completeApplied(String proposalId, String serviceActor, String domainObjectId,
      String requestFingerprint) {
    AgentProposal proposal = requireProposal(proposalId);
    proposal.markApplied(domainObjectId, Ids.nowIso());
    proposalRepository.save(proposal);
    auditService.append(proposalId, AgentWriteAudit.STAGE_APPLY, "service", serviceActor, "ok",
        requestFingerprint, domainObjectId);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void completeFailed(String proposalId, String serviceActor, String reason) {
    AgentProposal proposal = requireProposal(proposalId);
    proposal.markFailed(reason, Ids.nowIso());
    proposalRepository.save(proposal);
    auditService.append(proposalId, AgentWriteAudit.STAGE_APPLY, "service", serviceActor,
        "failed:" + reason, null, null);
  }

  private AgentProposal requireProposal(String proposalId) {
    return proposalRepository.findById(proposalId)
        .orElseThrow(() -> ApiException.notFound("proposal not found: " + proposalId));
  }
}
