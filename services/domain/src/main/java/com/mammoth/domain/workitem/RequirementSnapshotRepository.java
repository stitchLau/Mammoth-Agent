package com.mammoth.domain.workitem;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementSnapshotRepository extends JpaRepository<RequirementSnapshot, String> {

  List<RequirementSnapshot> findByWorkItemIdOrderByVersionAsc(String workItemId);

  Optional<RequirementSnapshot> findTopByWorkItemIdOrderByVersionDesc(String workItemId);

  long countByWorkItemId(String workItemId);
}
