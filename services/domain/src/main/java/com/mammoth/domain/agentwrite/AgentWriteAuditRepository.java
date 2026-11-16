package com.mammoth.domain.agentwrite;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentWriteAuditRepository extends JpaRepository<AgentWriteAudit, String> {

  List<AgentWriteAudit> findByProposalIdOrderByAtAscIdAsc(String proposalId);

  Optional<AgentWriteAudit> findFirstByProposalIdOrderByAtDescIdDesc(String proposalId);
}
