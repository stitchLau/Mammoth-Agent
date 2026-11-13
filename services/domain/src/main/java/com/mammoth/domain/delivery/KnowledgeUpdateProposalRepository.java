package com.mammoth.domain.delivery;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeUpdateProposalRepository
    extends JpaRepository<KnowledgeUpdateProposal, String> {

  List<KnowledgeUpdateProposal> findByWorkItemIdOrderByCreatedAtAsc(String workItemId);

  Optional<KnowledgeUpdateProposal> findByIdAndProjectId(String id, String projectId);

  Optional<KnowledgeUpdateProposal> findByIdAndWorkItemId(String id, String workItemId);
}
