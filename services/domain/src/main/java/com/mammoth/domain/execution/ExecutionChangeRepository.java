package com.mammoth.domain.execution;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionChangeRepository extends JpaRepository<ExecutionChange, String> {

  List<ExecutionChange> findByExecutionRecordIdOrderByFilePathAsc(String executionRecordId);

  List<ExecutionChange> findByExecutionRecordIdInOrderByFilePathAsc(List<String> executionRecordIds);
}
