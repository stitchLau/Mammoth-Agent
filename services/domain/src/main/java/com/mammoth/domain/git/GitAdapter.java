package com.mammoth.domain.git;

import java.nio.file.Path;
import java.util.List;

/**
 * Read-only Git inspection boundary (ADR-0007).
 *
 * <p>Mammoth never clones, fetches, pulls, pushes, checks out or writes a working tree. Implementations
 * may only run the read-only command whitelist documented in {@link LocalGitAdapter}.
 */
public interface GitAdapter {

  /** Snapshot of one repository at inspection time. */
  record Snapshot(String workingTreeRoot, String headCommit, String branch, boolean detached,
      boolean dirty, boolean worktree, String remoteUrl) {
  }

  /** One file-level change between two commits (no content). */
  record FileChange(String path, String changeType, String oldPath, int additions,
      int deletions) {
  }

  /**
   * Inspect a repository path (a working tree root or a subdirectory of it).
   *
   * @throws com.mammoth.domain.web.ApiException 400 when the path is not a usable Git repository
   */
  Snapshot inspect(Path repositoryPath);

  /** File changes between two commits; commits must exist in the repository. */
  List<FileChange> diff(Path repositoryPath, String fromCommit, String toCommit);

  /** True when {@code ancestor} is reachable from {@code descendant}. */
  boolean isAncestor(Path repositoryPath, String ancestor, String descendant);

  /** Fail with an actionable error when the commit does not exist in the repository. */
  void verifyCommit(Path repositoryPath, String commit);
}
