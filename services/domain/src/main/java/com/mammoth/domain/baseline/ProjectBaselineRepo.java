package com.mammoth.domain.baseline;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Per-repository commit of a {@link ProjectBaseline}. */
@Entity
@Table(name = "project_baseline_repo")
public class ProjectBaselineRepo {

  @Id
  private String id;

  private String baselineId;

  private String repositoryId;

  private String commitSha;

  private String branch;

  private boolean dirty;

  private boolean detached;

  private boolean worktree;

  private String capturedAt;

  protected ProjectBaselineRepo() {
  }

  public ProjectBaselineRepo(String id, String baselineId, String repositoryId, String commitSha,
      String branch, boolean dirty, boolean detached, boolean worktree, String capturedAt) {
    this.id = id;
    this.baselineId = baselineId;
    this.repositoryId = repositoryId;
    this.commitSha = commitSha;
    this.branch = branch;
    this.dirty = dirty;
    this.detached = detached;
    this.worktree = worktree;
    this.capturedAt = capturedAt;
  }

  public String getId() {
    return id;
  }

  public String getBaselineId() {
    return baselineId;
  }

  public String getRepositoryId() {
    return repositoryId;
  }

  public String getCommitSha() {
    return commitSha;
  }

  public String getBranch() {
    return branch;
  }

  public boolean isDirty() {
    return dirty;
  }

  public boolean isDetached() {
    return detached;
  }

  public boolean isWorktree() {
    return worktree;
  }

  public String getCapturedAt() {
    return capturedAt;
  }
}
