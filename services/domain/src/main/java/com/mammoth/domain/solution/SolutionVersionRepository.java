package com.mammoth.domain.solution;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolutionVersionRepository extends JpaRepository<SolutionVersion, String> {

  List<SolutionVersion> findByWorkItemIdOrderByVersionAsc(String workItemId);

  Optional<SolutionVersion> findByIdAndWorkItemId(String id, String workItemId);

  Optional<SolutionVersion> findTopByWorkItemIdOrderByVersionDesc(String workItemId);

  List<SolutionVersion> findByWorkItemIdAndStatus(String workItemId, String status);
}
