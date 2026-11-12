package com.mammoth.domain.workitem;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkItemRepository extends JpaRepository<WorkItem, String> {

  List<WorkItem> findByProjectIdOrderByCreatedAtAsc(String projectId);

  List<WorkItem> findByProjectIdAndStatusOrderByCreatedAtAsc(String projectId, String status);

  Optional<WorkItem> findByIdAndProjectId(String id, String projectId);

  long countByProjectId(String projectId);
}
