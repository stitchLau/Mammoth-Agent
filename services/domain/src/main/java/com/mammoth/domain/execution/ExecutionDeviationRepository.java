package com.mammoth.domain.execution;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionDeviationRepository extends JpaRepository<ExecutionDeviation, String> {

  List<ExecutionDeviation> findByExecutionRecordIdOrderByCreatedAtAsc(String executionRecordId);

  List<ExecutionDeviation> findByExecutionRecordIdInOrderByCreatedAtAsc(
      List<String> executionRecordIds);
}
