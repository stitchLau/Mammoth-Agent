package com.mammoth.domain.agentwrite;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentProposalRepository extends JpaRepository<AgentProposal, String> {

  Optional<AgentProposal> findByIdAndProjectId(String id, String projectId);

  Optional<AgentProposal> findByIdempotencyKey(String idempotencyKey);

  List<AgentProposal> findByProjectIdOrderByCreatedAtAsc(String projectId);

  List<AgentProposal> findByProjectIdAndStatusOrderByCreatedAtAsc(String projectId, String status);
}
