package com.mammoth.domain.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mammoth.domain.artifact.ArtifactRecord;
import com.mammoth.domain.artifact.ArtifactService;
import com.mammoth.domain.knowledge.KnowledgeSearchAdapter;
import com.mammoth.domain.knowledge.KnowledgeService;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.Customer;
import com.mammoth.domain.persistence.Project;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.service.CatalogService;
import com.mammoth.domain.service.ProjectService;
import com.mammoth.domain.service.UserService;
import com.mammoth.domain.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * T10 acceptance: permission isolation across projects, roles and artifact/knowledge surfaces.
 * Deny-by-default must hold for every path that is not explicitly allowed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PermissionIsolationTest {

  private static final Path DATA_ROOT = createDataRoot();

  private static Path createDataRoot() {
    try {
      return Files.createTempDirectory("mammoth-t10-it");
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
  private ArtifactService artifactService;

  @Autowired
  private KnowledgeService knowledgeService;

  @Autowired
  private KnowledgeSearchAdapter searchAdapter;

  @Autowired
  private AuthorizationService authorizationService;

  private String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private AppUser user(String prefix) {
    return userService.create(unique(prefix), "User", "Passw0rd!2026");
  }

  private Project project(AppUser owner) {
    Customer customer = catalogService.createCustomer(owner, unique("ACME"));
    return projectService.create(owner, customer.getId(), unique("PRJ"), "isolation project");
  }

  private ArtifactRecord artifact(AppUser owner, Project project, String text) {
    return artifactService.upload(owner, project.getId(), "source", unique("f") + ".md",
        "text/markdown", null, text.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void crossProjectArtifactAndKnowledgeAccessIsDeniedWithoutLeakingExistence() {
    AppUser owner = user("owner");
    Project projectA = project(owner);
    Project projectB = project(owner);
    ArtifactRecord artifactA = artifact(owner, projectA, "A 项目资料");
    var documentA = knowledgeService.create(owner, projectA.getId(), "process", "A 项目知识",
        "A 项目：收货流程条码重复时冻结库存。",
        List.of(new KnowledgeService.SourceRefInput(artifactA.getId(), null, null)));

    // Same owner, wrong project scope: the artifact must not resolve across project boundaries.
    assertThatThrownBy(() -> artifactService.require(projectB.getId(), artifactA.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("artifact not found");
    assertThatThrownBy(() -> knowledgeService.requireDocument(projectB.getId(), documentA.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("knowledge document not found");
  }

  @Test
  void viewerCannotWriteArtifactsOrKnowledge() {
    AppUser owner = user("owner2");
    AppUser viewer = user("viewer2");
    Project project = project(owner);
    projectService.addMember(owner, project.getId(), viewer.getId(), AuthorizationService.VIEWER);

    assertThatThrownBy(() -> authorizationService.requireProjectRole(
        authorizationService.requireActiveUser(viewer.getId()), project.getId(),
        ArtifactService.WRITE_ROLES))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cannot perform this action");

    ArtifactRecord artifact = artifact(owner, project, "owner 上传的资料");
    assertThatThrownBy(() -> knowledgeService.create(viewer, project.getId(), "process", "越权知识",
        "viewer 不应能创建知识。",
        List.of(new KnowledgeService.SourceRefInput(artifact.getId(), null, null))))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cannot edit knowledge");
  }

  @Test
  void searchNeverLeaksDraftsOrOtherProjects() {
    AppUser owner = user("owner3");
    AppUser reviewer = user("reviewer3");
    Project projectA = project(owner);
    Project projectB = project(owner);
    projectService.addMember(owner, projectA.getId(), reviewer.getId(), AuthorizationService.REVIEWER);
    projectService.addMember(owner, projectB.getId(), reviewer.getId(), AuthorizationService.REVIEWER);

    ArtifactRecord artifactA = artifact(owner, projectA, "A 资料");
    var verifiedA = knowledgeService.create(owner, projectA.getId(), "process", "A 已验证知识",
        "收货流程条码重复时冻结库存并人工复核。",
        List.of(new KnowledgeService.SourceRefInput(artifactA.getId(), null, null)));
    knowledgeService.submitForReview(owner, projectA.getId(), verifiedA.getId());
    knowledgeService.verify(reviewer, projectA.getId(), verifiedA.getId());

    var draftA = knowledgeService.create(owner, projectA.getId(), "process", "A 草稿知识",
        "草稿：收货流程条码重复的另一种处理。",
        List.of(new KnowledgeService.SourceRefInput(artifactA.getId(), null, null)));

    ArtifactRecord artifactB = artifact(owner, projectB, "B 资料");
    var verifiedB = knowledgeService.create(owner, projectB.getId(), "process", "B 已验证知识",
        "收货流程条码重复时冻结库存（B 项目版本）。",
        List.of(new KnowledgeService.SourceRefInput(artifactB.getId(), null, null)));
    knowledgeService.submitForReview(owner, projectB.getId(), verifiedB.getId());
    knowledgeService.verify(reviewer, projectB.getId(), verifiedB.getId());

    var hitsA = searchAdapter.search(projectA.getId(), "条码重复", 10);
    assertThat(hitsA).isNotEmpty();
    assertThat(hitsA).allSatisfy(hit -> {
      assertThat(hit.documentId()).isNotEqualTo(draftA.getId());
      assertThat(hit.documentId()).isNotEqualTo(verifiedB.getId());
    });
    assertThat(hitsA).extracting(hit -> hit.documentId()).contains(verifiedA.getId());
  }

  @Test
  void outsiderCannotReadAnyProjectSurface() {
    AppUser owner = user("owner4");
    AppUser outsider = user("outsider4");
    Project project = project(owner);
    ArtifactRecord artifact = artifact(owner, project, "受保护资料");

    Set<String> anyRole = Set.of(AuthorizationService.OWNER, AuthorizationService.EDITOR,
        AuthorizationService.REVIEWER, AuthorizationService.VIEWER);

    assertThatThrownBy(() -> authorizationService.requireProjectRole(outsider, project.getId(), anyRole))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("no access to project");
    assertThatThrownBy(() -> artifactService.require(project.getId(), "missing-artifact"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("artifact not found");
    assertThat(artifact.getId()).isNotBlank();
  }
}
