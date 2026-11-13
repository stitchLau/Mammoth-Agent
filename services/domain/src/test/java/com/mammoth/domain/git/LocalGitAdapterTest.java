package com.mammoth.domain.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mammoth.domain.web.ApiException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ADR-0007 acceptance: read-only Git inspection, diff and ancestry on a disposable repository. */
class LocalGitAdapterTest {

  @TempDir
  Path tempDir;

  private final LocalGitAdapter adapter = new LocalGitAdapter();

  @BeforeAll
  static void requireGit() {
    GitFixtures.assumeGitAvailable();
  }

  @Test
  void inspectReturnsHeadBranchDirtinessAndSanitizedRemote() throws Exception {
    Path repo = GitFixtures.initRepo(tempDir.resolve("repo"));
    GitFixtures.commit(repo, "docs/流程.md", "第一版流程", "initial");
    GitFixtures.remote(repo, "https://user:secret-token@example.com/team/repo.git");

    GitAdapter.Snapshot snapshot = adapter.inspect(repo);

    assertThat(snapshot.workingTreeRoot()).isEqualTo(repo.toAbsolutePath().normalize().toString());
    assertThat(snapshot.headCommit()).hasSize(40);
    assertThat(snapshot.branch()).isEqualTo("main");
    assertThat(snapshot.detached()).isFalse();
    assertThat(snapshot.dirty()).isFalse();
    assertThat(snapshot.worktree()).isFalse();
    assertThat(snapshot.remoteUrl()).isEqualTo("https://example.com/team/repo.git");
    assertThat(snapshot.remoteUrl()).doesNotContain("secret-token");

    Files.writeString(repo.resolve("dirty.txt"), "uncommitted");
    assertThat(adapter.inspect(repo).dirty()).isTrue();
  }

  @Test
  void diffAndAncestryWorkAcrossCommits() throws Exception {
    Path repo = GitFixtures.initRepo(tempDir.resolve("repo2"));
    String first = GitFixtures.commit(repo, "src/App.java", "class App {}", "first");
    String second = GitFixtures.commit(repo, "src/App.java", "class App { int x; }", "second");
    Files.writeString(repo.resolve("src/New.java"), "class New {}");
    GitFixtures.git(repo, "add", "-A");
    GitFixtures.git(repo, "commit", "-m", "third");
    String third = GitFixtures.head(repo);

    List<GitAdapter.FileChange> changes = adapter.diff(repo, first, third);
    assertThat(changes).isNotEmpty();
    assertThat(changes).anyMatch(change -> change.path().equals("src/New.java")
        && change.changeType().equals("A"));
    assertThat(changes).anyMatch(change -> change.path().equals("src/App.java")
        && change.changeType().equals("M"));

    assertThat(adapter.isAncestor(repo, first, third)).isTrue();
    assertThat(adapter.isAncestor(repo, third, first)).isFalse();
    assertThat(adapter.isAncestor(repo, second, third)).isTrue();
    assertThat(adapter.diff(repo, third, first)).isNotEmpty();
  }

  @Test
  void invalidPathAndCommitAreRejected() throws Exception {
    Path repo = GitFixtures.initRepo(tempDir.resolve("repo3"));
    GitFixtures.commit(repo, "a.txt", "a", "first");

    assertThatThrownBy(() -> adapter.inspect(tempDir.resolve("missing")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("does not exist");

    Path plainDirectory = Files.createDirectories(tempDir.resolve("plain"));
    assertThatThrownBy(() -> adapter.inspect(plainDirectory))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("not a usable git repository");

    assertThatThrownBy(() -> adapter.verifyCommit(repo, "deadbeef"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("does not exist");

    assertThatThrownBy(() -> adapter.verifyCommit(repo, "--upload-pack=evil"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("invalid commit id");
  }
}
