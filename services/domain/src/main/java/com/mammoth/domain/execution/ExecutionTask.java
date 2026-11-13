package com.mammoth.domain.execution;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Completion state of a solution task at execution time. */
@Entity
@Table(name = "execution_task")
public class ExecutionTask {

  public static final String DONE = "done";
  public static final String PARTIAL = "partial";
  public static final String SKIPPED = "skipped";

  @Id
  private String id;

  private String executionRecordId;

  private String taskId;

  private String status;

  private String note;

  protected ExecutionTask() {
  }

  public ExecutionTask(String id, String executionRecordId, String taskId, String status,
      String note) {
    this.id = id;
    this.executionRecordId = executionRecordId;
    this.taskId = taskId;
    this.status = status;
    this.note = note;
  }

  public String getId() {
    return id;
  }

  public String getExecutionRecordId() {
    return executionRecordId;
  }

  public String getTaskId() {
    return taskId;
  }

  public String getStatus() {
    return status;
  }

  public String getNote() {
    return note;
  }
}
