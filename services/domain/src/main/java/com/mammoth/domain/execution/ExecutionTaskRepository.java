package com.mammoth.domain.execution;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionTaskRepository extends JpaRepository<ExecutionTask, String> {

  List<ExecutionTask> findByExecutionRecordId(String executionRecordId);

  List<ExecutionTask> findByExecutionRecordIdIn(List<String> executionRecordIds);

  boolean existsByTaskId(String taskId);
}
