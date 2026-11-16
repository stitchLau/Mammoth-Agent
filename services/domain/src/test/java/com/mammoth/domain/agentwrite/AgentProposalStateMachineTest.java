package com.mammoth.domain.agentwrite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mammoth.domain.web.ApiException;
import org.junit.jupiter.api.Test;

/** ADR-0015 P1: proposal state machine rules, including four-eyes and high-risk second approval. */
class AgentProposalStateMachineTest {

  private static final String NOW = "2026-11-16T12:00:00Z";
  private static final String PROPOSER = "agent-user";
  private static final String APPROVER = "human-owner";

  private AgentProposal proposal(String risk) {
    return new AgentProposal("proposal-1", "project-1", "work_item.create",
        "{\"title\":\"t\"}", "summary", AgentProposal.PROPOSER_AGENT, PROPOSER, "session-1",
        "idem-1", risk, NOW);
  }

  @Test
  void normalRiskApprovalNeedsOneHumanAndMarksApproved() {
    AgentProposal proposal = proposal(AgentProposal.RISK_NORMAL);
    assertThat(proposal.getStatus()).isEqualTo(AgentProposal.STATUS_PENDING);
    assertThat(proposal.isHighRisk()).isFalse();

    proposal.approvePrimary(APPROVER, "2026-11-16T12:01:00Z");

    assertThat(proposal.getStatus()).isEqualTo(AgentProposal.STATUS_APPROVED);
    assertThat(proposal.getDecidedAt()).isEqualTo("2026-11-16T12:01:00Z");
  }

  @Test
  void highRiskApprovalNeedsASecondDifferentHuman() {
    AgentProposal proposal = proposal(AgentProposal.RISK_HIGH);
    assertThat(proposal.isHighRisk()).isTrue();

    proposal.approvePrimary(APPROVER, "2026-11-16T12:01:00Z");
    assertThat(proposal.getStatus()).isEqualTo(AgentProposal.STATUS_AWAITING_SECOND);
    assertThat(proposal.getDecidedAt()).isNull();

    proposal.approveSecond("human-reviewer", "2026-11-16T12:02:00Z");
    assertThat(proposal.getStatus()).isEqualTo(AgentProposal.STATUS_APPROVED);
    assertThat(proposal.getDecidedAt()).isEqualTo("2026-11-16T12:02:00Z");
  }

  @Test
  void secondApprovalOnNormalRiskIsRejected() {
    AgentProposal proposal = proposal(AgentProposal.RISK_NORMAL);
    proposal.approvePrimary(APPROVER, NOW);
    assertThatThrownBy(() -> proposal.approveSecond("human-reviewer", NOW))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("high-risk");
  }

  @Test
  void secondApprovalRequiresPrimaryApprovalFirst() {
    AgentProposal proposal = proposal(AgentProposal.RISK_HIGH);
    assertThatThrownBy(() -> proposal.approveSecond("human-reviewer", NOW))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("not awaiting");
  }

  @Test
  void proposerCannotApproveOwnProposal() {
    AgentProposal proposal = proposal(AgentProposal.RISK_NORMAL);
    assertThatThrownBy(() -> proposal.approvePrimary(PROPOSER, NOW))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("proposer");
    assertThat(proposal.getStatus()).isEqualTo(AgentProposal.STATUS_PENDING);
  }

  @Test
  void highRiskSecondApproverCannotBeTheProposerEither() {
    AgentProposal proposal = proposal(AgentProposal.RISK_HIGH);
    proposal.approvePrimary(APPROVER, NOW);
    assertThatThrownBy(() -> proposal.approveSecond(PROPOSER, NOW))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("proposer");
    assertThat(proposal.getStatus()).isEqualTo(AgentProposal.STATUS_AWAITING_SECOND);
  }

  @Test
  void rejectionRequiresAReasonAndClosesTheProposal() {
    AgentProposal proposal = proposal(AgentProposal.RISK_NORMAL);
    assertThatThrownBy(() -> proposal.reject("  ", NOW))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("reason");

    proposal.reject("not needed", NOW);
    assertThat(proposal.getStatus()).isEqualTo(AgentProposal.STATUS_REJECTED);
    assertThat(proposal.getFailureReason()).isEqualTo("not needed");
    assertThat(proposal.isOpen()).isFalse();
  }

  @Test
  void decidedProposalCannotBeApprovedOrRejectedAgain() {
    AgentProposal proposal = proposal(AgentProposal.RISK_NORMAL);
    proposal.approvePrimary(APPROVER, NOW);
    assertThatThrownBy(() -> proposal.approvePrimary("another", NOW))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cannot approve");
    assertThatThrownBy(() -> proposal.reject("late", NOW))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cannot reject");
  }

  @Test
  void applyLifecycleIsIdempotentForRetries() {
    AgentProposal proposal = proposal(AgentProposal.RISK_NORMAL);
    proposal.approvePrimary(APPROVER, NOW);

    proposal.markApplying();
    proposal.markApplying();
    assertThat(proposal.getStatus()).isEqualTo(AgentProposal.STATUS_APPLYING);

    proposal.markApplied("work-item-1", "2026-11-16T12:03:00Z");
    proposal.markApplied("work-item-1", "2026-11-16T12:03:00Z");
    assertThat(proposal.getStatus()).isEqualTo(AgentProposal.STATUS_APPLIED);
    assertThat(proposal.getAppliedAt()).isEqualTo("2026-11-16T12:03:00Z");
  }

  @Test
  void applyIsRejectedBeforeApproval() {
    AgentProposal proposal = proposal(AgentProposal.RISK_NORMAL);
    assertThatThrownBy(proposal::markApplying)
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("not approved");
  }

  @Test
  void applyFailureIsRecorded() {
    AgentProposal proposal = proposal(AgentProposal.RISK_NORMAL);
    proposal.approvePrimary(APPROVER, NOW);
    proposal.markApplying();
    proposal.markFailed("domain rejected", "2026-11-16T12:04:00Z");
    assertThat(proposal.getStatus()).isEqualTo(AgentProposal.STATUS_FAILED);
    assertThat(proposal.getFailureReason()).isEqualTo("domain rejected");
  }

  @Test
  void pendingProposalCanExpireButApprovedOneCannot() {
    AgentProposal pending = proposal(AgentProposal.RISK_NORMAL);
    pending.expire(NOW);
    assertThat(pending.getStatus()).isEqualTo(AgentProposal.STATUS_EXPIRED);

    AgentProposal approved = proposal(AgentProposal.RISK_NORMAL);
    approved.approvePrimary(APPROVER, NOW);
    assertThatThrownBy(() -> approved.expire(NOW))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cannot expire");
  }

  @Test
  void riskLevelIsValidatedAndDefaultsToNormal() {
    assertThat(AgentProposal.normalizeRisk(null)).isEqualTo(AgentProposal.RISK_NORMAL);
    assertThat(AgentProposal.normalizeRisk("HIGH")).isEqualTo(AgentProposal.RISK_HIGH);
    assertThatThrownBy(() -> AgentProposal.normalizeRisk("extreme"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("unsupported risk level");
  }
}
