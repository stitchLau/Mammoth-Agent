package com.mammoth.domain.execution;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One file-level change of an execution record (path/type/counts only, no code). */
@Entity
@Table(name = "execution_change")
public class ExecutionChange {

  @Id
  private String id;

  private String executionRecordId;

  private String filePath;

  private String changeType;

  private String oldPath;

  private int additions;

  private int deletions;

  protected ExecutionChange() {
  }

  public ExecutionChange(String id, String executionRecordId, String filePath, String changeType,
      String oldPath, int additions, int deletions) {
    this.id = id;
    this.executionRecordId = executionRecordId;
    this.filePath = filePath;
    this.changeType = changeType;
    this.oldPath = oldPath;
    this.additions = additions;
    this.deletions = deletions;
  }

  public String getId() {
    return id;
  }

  public String getExecutionRecordId() {
    return executionRecordId;
  }

  public String getFilePath() {
    return filePath;
  }

  public String getChangeType() {
    return changeType;
  }

  public String getOldPath() {
    return oldPath;
  }

  public int getAdditions() {
    return additions;
  }

  public int getDeletions() {
    return deletions;
  }
}
