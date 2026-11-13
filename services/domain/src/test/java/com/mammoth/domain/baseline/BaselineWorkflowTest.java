package com.mammoth.domain.baseline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mammoth.domain.git.GitFixtures;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.Customer;
import com.mammoth.domain.persistence.Project;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.service.CatalogService;
import com.mammoth.domain.service.ProjectService;
import com.mammoth.domain.service.UserService;
import com.mammoth.domain.web.ApiException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** M3 S2 acceptance: repository registration and immutable baseline capture (ADR-0007). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class BaselineWorkflowTest {

  private static final Path DATA_ROOT = createDataRoot();
  private static final Path REPO_ROOT = createRepoRoot();

  private static Path createDataRoot() {
    try {
      return Files.createTempDirectory("mammoth-m3-baseline");
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  private static Path createRepoRoot() {
    try {
      return Files.createTempDirectory("mammoth-m3-baseline-repos");
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("mammoth.data-root", DATA_ROOT::toString);
  }

  @Autowired
  private UserService userService;

  @Autowired
  private CatalogService catalogService;

  @Autowired
  private ProjectService projectService;

  @Autowired
  private ProjectRepositoryService repositoryService;

  @Autowired
  private BaselineService baselineService;

  @BeforeAll
  static void requireGit() {
    GitFixtures.assumeGitAvailable();
  }

  private String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private AppUser user(String prefix) {
    return userService.create(unique(prefix), "User", "Passw0rd!2026");
  }

  private Project project(AppUser owner) {
    Customer customer = catalogService.createCustomer(owner, unique("ACME"));
    return projectService.create(owner, customer.getId(), unique("PRJ"), "baseline project");
  }

  @Test
  void registersRepositoryAndCapturesImmutableBaseline() throws Exception {
    AppUser owner = user("owner");
    Project project = project(owner);
    Path repositoryPath = GitFixtures.createRepository(REPO_ROOT, "repo-" + unique("git"));
    GitFixtures.initRepo(repositoryPath);
    String head = GitFixtures.commit(repositoryPath, "src/Main.java", "class Main {}", "initial");
    GitFixtures.remote(repositoryPath, "https://user:secret@example.com/team/repo.git");

    ProjectRepository repository = repositoryService.register(owner, project.getId(), "backend",
        repositoryPath.toString(), "main");
    assertThat(repository.getAbsolutePath())
        .isEqualTo(repositoryPath.toAbsolutePath().normalize().toString());
    assertThat(repository.getRemoteUrl()).isEqualTo("https://example.com/team/repo.git");
    assertThat(repository.isActive()).isTrue();

    BaselineService.Detail baseline = baselineService.capture(owner, project.getId(), "M3 start",
        false);
    assertThat(baseline.baseline().isComplete()).isTrue();
    assertThat(baseline.repos()).hasSize(1);
    assertThat(baseline.repos().get(0).getCommitSha()).isEqualTo(head);
    assertThat(baseline.repos().get(0).getBranch()).isEqualTo("main");
    assertThat(baseline.repos().get(0).isDirty()).isFalse();

    assertThat(baselineService.list(owner, project.getId())).hasSize(1);
    assertThat(baselineService.latest(project.getId()).getId())
        .isEqualTo(baseline.baseline().getId());

    Files.writeString(repositoryPath.resolve("dirty.txt"), "uncommitted");
    assertThatThrownBy(() -> baselineService.capture(owner, project.getId(), "dirty", false))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("dirty")
        .hasMessageContaining("backend");

    BaselineService.Detail dirty = baselineService.capture(owner, project.getId(), "accepted dirty",
        true);
    assertThat(dirty.repos().get(0).isDirty()).isTrue();
    assertThat(baselineService.list(owner, project.getId())).hasSize(2);

    assertThatThrownBy(() -> repositoryService.register(owner, project.getId(), "dup",
        repositoryPath.toString(), null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("already registered");
  }

  @Test
  void permissionsAndInvalidPathsAreEnforced() throws Exception {
    AppUser owner = user("owner2");
    AppUser editor = user("editor2");
    AppUser viewer = user("viewer2");
    Project project = project(owner);
    projectService.addMember(owner, project.getId(), editor.getId(), AuthorizationService.EDITOR);
    projectService.addMember(owner, project.getId(), viewer.getId(), AuthorizationService.VIEWER);

    Path repositoryPath = GitFixtures.createRepository(REPO_ROOT, "repo-" + unique("git"));
    GitFixtures.initRepo(repositoryPath);
    GitFixtures.commit(repositoryPath, "a.txt", "a", "initial");

    assertThatThrownBy(() -> repositoryService.register(viewer, project.getId(), "viewer repo",
        repositoryPath.toString(), null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cannot perform this action");

    assertThatThrownBy(() -> repositoryService.register(owner, project.getId(), "not a repo",
        REPO_ROOT.resolve("plain-" + unique("dir")).toString(), null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("does not exist");

    ProjectRepository repository = repositoryService.register(owner, project.getId(), "backend",
        repositoryPath.toString(), null);
    assertThat(repository.getProjectId()).isEqualTo(project.getId());

    assertThat(baselineService.capture(editor, project.getId(), "editor capture", false)
        .repos()).hasSize(1);
    assertThatThrownBy(() -> baselineService.capture(viewer, project.getId(), "viewer capture",
        false))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cannot perform this action");

    ProjectRepository deactivated = repositoryService.deactivate(owner, project.getId(),
        repository.getId());
    assertThat(deactivated.isActive()).isFalse();
    assertThatThrownBy(() -> baselineService.capture(owner, project.getId(), "no active repo",
        false))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("no active repository");
  }
}
