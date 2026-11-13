package com.mammoth.domain.execution;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionRecordRepository extends JpaRepository<ExecutionRecord, String> {

  List<ExecutionRecord> findByWorkItemIdOrderByRecordedAtAsc(String workItemId);

  List<ExecutionRecord> findByWorkItemIdAndSolutionVersionIdOrderByRecordedAtAsc(String workItemId,
      String solutionVersionId);

  Optional<ExecutionRecord> findByIdAndProjectId(String id, String projectId);
}
