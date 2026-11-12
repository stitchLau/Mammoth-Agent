package com.mammoth.domain.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mammoth.domain.artifact.ArtifactRecord;
import com.mammoth.domain.artifact.ArtifactService;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.AuditLogRepository;
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

/** T6 + T8 acceptance: knowledge lifecycle, human review gate, evidence refs and scope-safe search. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class KnowledgeWorkflowTest {

  private static final Path DATA_ROOT = createDataRoot();

  private static Path createDataRoot() {
    try {
      return Files.createTempDirectory("mammoth-t6-it");
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
  private AuthorizationService authorizationService;

  @Autowired
  private ArtifactService artifactService;

  @Autowired
  private KnowledgeService knowledgeService;

  @Autowired
  private KnowledgeSearchAdapter searchAdapter;

  @Autowired
  private AuditLogRepository auditLogRepository;

  private String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private AppUser user(String prefix) {
    return userService.create(unique(prefix), "User", "Passw0rd!2026");
  }

  private Project project(AppUser owner) {
    Customer customer = catalogService.createCustomer(owner, unique("ACME"));
    return projectService.create(owner, customer.getId(), unique("PRJ"), "knowledge project");
  }

  private ArtifactRecord artifact(AppUser owner, Project project, String text) {
    return artifactService.upload(owner, project.getId(), "source", unique("notes") + ".md",
        "text/markdown", null, text.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void requiresEvidenceAndCannotVerifyOwnDocument() {
    AppUser owner = user("owner");
    Project project = project(owner);
    ArtifactRecord artifact = artifact(owner, project, "收货流程说明：条码重复时冻结库存。");

    assertThatThrownBy(() -> knowledgeService.create(
        owner, project.getId(), "process", "收货流程", "正文", List.of()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("at least one artifact");

    KnowledgeDocument draft = knowledgeService.create(owner, project.getId(), "process", "收货流程",
        "收货流程中条码重复时先冻结库存再人工复核，异常需当天闭环。",
        List.of(new KnowledgeService.SourceRefInput(artifact.getId(), "§2", "原始纪要")));

    assertThat(draft.getStatus()).isEqualTo(KnowledgeDocument.DRAFT);
    assertThat(knowledgeService.sourceRefs(draft.getId())).hasSize(1);
    assertThat(knowledgeService.chunkCount(draft.getId())).isPositive();

    assertThatThrownBy(() -> knowledgeService.verify(owner, project.getId(), draft.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("only documents in needs_review can be verified");

    knowledgeService.submitForReview(owner, project.getId(), draft.getId());
    assertThatThrownBy(() -> knowledgeService.verify(owner, project.getId(), draft.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("creator cannot verify");
  }

  @Test
  void reviewerVerifiesDocumentAndAuditIsRecorded() {
    AppUser owner = user("owner2");
    AppUser reviewerUser = user("reviewer2");
    Project project = project(owner);
    projectService.addMember(owner, project.getId(), reviewerUser.getId(), AuthorizationService.REVIEWER);
    ArtifactRecord artifact = artifact(owner, project, "质检不合格流程说明。");

    KnowledgeDocument document = knowledgeService.create(owner, project.getId(), "process", "质检处理",
        "质检不合格时进入待处理区，隔离库存并通知供应商，退货需质量经理审批。",
        List.of(new KnowledgeService.SourceRefInput(artifact.getId(), "全篇", null)));
    knowledgeService.submitForReview(owner, project.getId(), document.getId());

    KnowledgeDocument verified = knowledgeService.verify(
        reviewerUser, project.getId(), document.getId());

    assertThat(verified.getStatus()).isEqualTo(KnowledgeDocument.VERIFIED);
    assertThat(verified.getReviewer()).isEqualTo(reviewerUser.getId());
    assertThat(verified.getReviewedAt()).isNotBlank();
    assertThat(auditLogRepository.countByAction("knowledge.verify")).isPositive();
  }

  @Test
  void viewerCannotVerifyAndOutsiderCannotSearch() {
    AppUser owner = user("owner3");
    AppUser viewer = user("viewer3");
    AppUser outsider = user("outsider3");
    Project project = project(owner);
    projectService.addMember(owner, project.getId(), viewer.getId(), AuthorizationService.VIEWER);
    ArtifactRecord artifact = artifact(owner, project, "盘点规则说明。");

    KnowledgeDocument document = knowledgeService.create(owner, project.getId(), "process", "盘点",
        "现场盘点分冻结盘与循环盘，差异超过阈值触发复盘流程。",
        List.of(new KnowledgeService.SourceRefInput(artifact.getId(), null, null)));
    knowledgeService.submitForReview(owner, project.getId(), document.getId());

    assertThatThrownBy(() -> knowledgeService.verify(viewer, project.getId(), document.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cannot verify knowledge");

    assertThatThrownBy(() -> authorizationService.requireProjectRole(
        outsider, project.getId(), Set.of(AuthorizationService.VIEWER)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("no access to project");
  }

  @Test
  void searchReturnsVerifiedKnowledgeOfThisProjectOnly() {
    AppUser owner = user("owner4");
    AppUser reviewerUser = user("reviewer4");
    Project project = project(owner);
    Project otherProject = project(owner);
    projectService.addMember(owner, project.getId(), reviewerUser.getId(), AuthorizationService.REVIEWER);

    ArtifactRecord artifact = artifact(owner, project, "收货流程异常处理原始资料。");
    KnowledgeDocument verified = knowledgeService.create(owner, project.getId(), "process", "收货流程异常",
        "收货流程中条码重复时，先冻结库存再人工复核，异常需当天闭环处理。",
        List.of(new KnowledgeService.SourceRefInput(artifact.getId(), "§2", null)));
    knowledgeService.submitForReview(owner, project.getId(), verified.getId());
    knowledgeService.verify(reviewerUser, project.getId(), verified.getId());

    ArtifactRecord draftArtifact = artifact(owner, project, "草稿资料。");
    knowledgeService.create(owner, project.getId(), "process", "草稿收货流程",
        "草稿：收货流程条码重复时的另一种处理方式。",
        List.of(new KnowledgeService.SourceRefInput(draftArtifact.getId(), null, null)));

    ArtifactRecord otherArtifact = artifact(owner, otherProject, "其他项目资料。");
    projectService.addMember(owner, otherProject.getId(), reviewerUser.getId(),
        AuthorizationService.REVIEWER);
    KnowledgeDocument other = knowledgeService.create(owner, otherProject.getId(), "process", "其他项目收货",
        "其他项目的收货流程条码重复处理说明。",
        List.of(new KnowledgeService.SourceRefInput(otherArtifact.getId(), null, null)));
    knowledgeService.submitForReview(owner, otherProject.getId(), other.getId());
    knowledgeService.verify(reviewerUser, otherProject.getId(), other.getId());

    var hits = searchAdapter.search(project.getId(), "条码重复怎么处理", 5);

    assertThat(hits).isNotEmpty();
    assertThat(hits).allSatisfy(hit -> assertThat(hit.documentId()).isEqualTo(verified.getId()));
    assertThat(searchAdapter.indexedChunkCount()).isPositive();
  }
}
