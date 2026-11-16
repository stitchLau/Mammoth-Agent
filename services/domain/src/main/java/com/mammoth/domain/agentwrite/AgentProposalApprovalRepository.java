package com.mammoth.domain.agentwrite;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentProposalApprovalRepository
    extends JpaRepository<AgentProposalApproval, String> {

  List<AgentProposalApproval> findByProposalIdOrderByDecidedAtAsc(String proposalId);

  Optional<AgentProposalApproval> findByProposalIdAndReviewerRole(String proposalId,
      String reviewerRole);

  boolean existsByProposalIdAndApproverUserId(String proposalId, String approverUserId);
}
