package com.mammoth.domain.workitem;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementEvidenceRepository extends JpaRepository<RequirementEvidence, String> {

  List<RequirementEvidence> findByWorkItemIdOrderByBatchNoAsc(String workItemId);

  Optional<RequirementEvidence> findTopByWorkItemIdOrderByBatchNoDesc(String workItemId);

  long countByWorkItemId(String workItemId);
}
