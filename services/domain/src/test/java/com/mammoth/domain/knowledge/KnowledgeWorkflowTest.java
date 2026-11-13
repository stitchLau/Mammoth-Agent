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

  @Test
  void draftCanBeEditedButNotAfterSubmit() {
    AppUser owner = user("owner5");
    Project project = project(owner);
    ArtifactRecord artifact = artifact(owner, project, "原始资料：盘点差异处理。");

    KnowledgeDocument draft = knowledgeService.create(owner, project.getId(), "process", "旧标题",
        "旧正文内容。", List.of(new KnowledgeService.SourceRefInput(artifact.getId(), "§1", null)));

    KnowledgeDocument updated = knowledgeService.updateDraft(owner, project.getId(), draft.getId(),
        "process", "新标题", "新的正文内容，用于验证草稿编辑与引用替换。",
        List.of(new KnowledgeService.SourceRefInput(artifact.getId(), "§2", "更新后的引用")));

    assertThat(updated.getTitle()).isEqualTo("新标题");
    assertThat(knowledgeService.sourceRefs(draft.getId())).hasSize(1);
    assertThat(knowledgeService.sourceRefs(draft.getId()).get(0).getLocator()).isEqualTo("§2");
    assertThat(knowledgeService.chunkCount(draft.getId())).isPositive();

    knowledgeService.submitForReview(owner, project.getId(), draft.getId());
    assertThatThrownBy(() -> knowledgeService.updateDraft(owner, project.getId(), draft.getId(),
        "process", "再改", "提交后不可编辑",
        List.of(new KnowledgeService.SourceRefInput(artifact.getId(), null, null))))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("only draft documents can be edited");
  }

  @Test
  void supersedeAndDeprecateKeepHistoryButRemoveFromSearch() {
    AppUser owner = user("owner6");
    AppUser reviewer = user("reviewer6");
    AppUser viewer = user("viewer6");
    Project project = project(owner);
    projectService.addMember(owner, project.getId(), reviewer.getId(), AuthorizationService.REVIEWER);
    projectService.addMember(owner, project.getId(), viewer.getId(), AuthorizationService.VIEWER);
    ArtifactRecord artifact = artifact(owner, project, "盘点差异处理原始资料。");

    KnowledgeDocument oldDocument = knowledgeService.create(owner, project.getId(), "process",
        "盘点差异流程（旧）", "盘点差异由主管手工处理，无系统冻结。",
        List.of(new KnowledgeService.SourceRefInput(artifact.getId(), "§1", null)));
    KnowledgeDocument newDocument = knowledgeService.create(owner, project.getId(), "process",
        "盘点差异流程（新）", "盘点差异先系统冻结库存，再由主管复核差异。",
        List.of(new KnowledgeService.SourceRefInput(artifact.getId(), "§2", null)));
    knowledgeService.submitForReview(owner, project.getId(), oldDocument.getId());
    knowledgeService.verify(reviewer, project.getId(), oldDocument.getId());
    knowledgeService.submitForReview(owner, project.getId(), newDocument.getId());
    knowledgeService.verify(reviewer, project.getId(), newDocument.getId());

    assertThat(searchAdapter.search(project.getId(), "盘点差异", 10)).hasSize(2);

    KnowledgeDocument superseded = knowledgeService.supersede(reviewer, project.getId(),
        oldDocument.getId(), newDocument.getId());
    assertThat(superseded.getStatus()).isEqualTo(KnowledgeDocument.SUPERSEDED);
    assertThat(superseded.getSupersededBy()).isEqualTo(newDocument.getId());
    assertThat(searchAdapter.search(project.getId(), "盘点差异", 10))
        .allSatisfy(hit -> assertThat(hit.documentId()).isEqualTo(newDocument.getId()));

    KnowledgeDocument deprecated = knowledgeService.deprecate(owner, project.getId(),
        newDocument.getId(), "已被下一版流程文档取代");
    assertThat(deprecated.getStatus()).isEqualTo(KnowledgeDocument.DEPRECATED);
    assertThat(deprecated.getDeprecationNote()).isEqualTo("已被下一版流程文档取代");
    assertThat(searchAdapter.search(project.getId(), "盘点差异", 10)).isEmpty();

    // History is still readable and auditable.
    assertThat(knowledgeService.requireDocument(project.getId(), oldDocument.getId()).getStatus())
        .isEqualTo(KnowledgeDocument.SUPERSEDED);
    assertThat(auditLogRepository.countByAction("knowledge.supersede")).isPositive();
    assertThat(auditLogRepository.countByAction("knowledge.deprecate")).isPositive();

    assertThatThrownBy(() -> knowledgeService.supersede(viewer, project.getId(),
        oldDocument.getId(), newDocument.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cannot change knowledge status");
    assertThatThrownBy(() -> knowledgeService.supersede(owner, project.getId(),
        oldDocument.getId(), newDocument.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("only verified knowledge");
    assertThatThrownBy(() -> knowledgeService.deprecate(owner, project.getId(),
        oldDocument.getId(), "again"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("only verified knowledge");
  }

  @Test
  void supersedeRequiresAVerifiedReplacementAndRejectsSelf() {
    AppUser owner = user("owner7");
    AppUser reviewer = user("reviewer7");
    Project project = project(owner);
    projectService.addMember(owner, project.getId(), reviewer.getId(), AuthorizationService.REVIEWER);
    ArtifactRecord artifact = artifact(owner, project, "规则资料。");

    KnowledgeDocument verified = knowledgeService.create(owner, project.getId(), "process",
        "规则（已验证）", "规则正文。",
        List.of(new KnowledgeService.SourceRefInput(artifact.getId(), null, null)));
    knowledgeService.submitForReview(owner, project.getId(), verified.getId());
    knowledgeService.verify(reviewer, project.getId(), verified.getId());
    KnowledgeDocument draft = knowledgeService.create(owner, project.getId(), "process",
        "规则（草稿）", "草稿正文。",
        List.of(new KnowledgeService.SourceRefInput(artifact.getId(), null, null)));

    assertThatThrownBy(() -> knowledgeService.supersede(reviewer, project.getId(),
        verified.getId(), verified.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cannot supersede itself");
    assertThatThrownBy(() -> knowledgeService.supersede(reviewer, project.getId(),
        verified.getId(), draft.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("replacement document must be verified");
  }}
