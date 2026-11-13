package com.mammoth.domain.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mammoth.domain.artifact.ArtifactRecord;
import com.mammoth.domain.artifact.ArtifactService;
import com.mammoth.domain.baseline.BaselineService;
import com.mammoth.domain.baseline.ProjectBaseline;
import com.mammoth.domain.baseline.ProjectRepository;
import com.mammoth.domain.baseline.ProjectRepositoryService;
import com.mammoth.domain.execution.ExecutionService;
import com.mammoth.domain.git.GitFixtures;
import com.mammoth.domain.knowledge.KnowledgeDocument;
import com.mammoth.domain.knowledge.KnowledgeSearchAdapter;
import com.mammoth.domain.knowledge.KnowledgeService;
import com.mammoth.domain.knowledge.KnowledgeStalenessService;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.AuditLogRepository;
import com.mammoth.domain.persistence.Customer;
import com.mammoth.domain.persistence.Project;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.service.CatalogService;
import com.mammoth.domain.service.ProjectService;
import com.mammoth.domain.service.UserService;
import com.mammoth.domain.solution.SolutionService;
import com.mammoth.domain.solution.SolutionVersion;
import com.mammoth.domain.web.ApiException;
import com.mammoth.domain.workitem.RequirementDimension;
import com.mammoth.domain.workitem.WorkItem;
import com.mammoth.domain.workitem.WorkItemService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** M3 acceptance: handoff, execution recovery, deviation, acceptance matrix and knowledge loop. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DeliveryWorkflowTest {

  private static final Path DATA_ROOT = createDataRoot();
  private static final Path REPO_ROOT = createRepoRoot();

  private static Path createDataRoot() {
    try {
      return Files.createTempDirectory("mammoth-m3-delivery");
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  private static Path createRepoRoot() {
    try {
      return Files.createTempDirectory("mammoth-m3-delivery-repos");
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
  private WorkItemService workItemService;

  @Autowired
  private SolutionService solutionService;

  @Autowired
  private ProjectRepositoryService repositoryService;

  @Autowired
  private BaselineService baselineService;

  @Autowired
  private ExecutionService executionService;

  @Autowired
  private DeliveryService deliveryService;

  @Autowired
  private HandoffPackageService handoffPackageService;

  @Autowired
  private ArtifactService artifactService;

  @Autowired
  private AuditLogRepository auditLogRepository;

  @Autowired
  private KnowledgeService knowledgeService;

  @Autowired
  private KnowledgeSearchAdapter searchAdapter;

  @Autowired
  private KnowledgeStalenessService stalenessService;

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
    return projectService.create(owner, customer.getId(), unique("PRJ"), "delivery project");
  }

  private record Fixture(AppUser owner, AppUser reviewer, Project project, Path repositoryPath,
      String repositoryId, String baselineId, String baselineCommit, WorkItem item,
      String solutionVersionId, String taskId, String criterionId) {
  }

  private Fixture approvedFixture() throws Exception {
    return approvedFixture("重复扫码时冻结库存并转人工复核。");
  }

  private Fixture approvedFixture(String businessBody) throws Exception {
    AppUser owner = user("owner");
    AppUser reviewer = user("reviewer");
    Project project = project(owner);
    projectService.addMember(owner, project.getId(), reviewer.getId(), AuthorizationService.REVIEWER);

    Path repositoryPath = GitFixtures.createRepository(REPO_ROOT, "repo-" + unique("git"));
    GitFixtures.initRepo(repositoryPath);
    String baselineCommit = GitFixtures.commit(repositoryPath, "src/App.java", "class App {}",
        "initial");
    ProjectRepository repository = repositoryService.register(owner, project.getId(), "backend",
        repositoryPath.toString(), "main");
    ProjectBaseline baseline = baselineService.capture(owner, project.getId(), "before work", false)
        .baseline();

    WorkItem item = workItemService.create(owner, project.getId(), "收货扫码重复入库",
        "客户口头描述：重复扫码会重复入库", "normal", null);
    workItemService.addEvidence(owner, project.getId(), item.getId(), "conversation",
        "现场反馈：同一托盘重复扫码会重复入库。", null, null, null);
    for (String dimension : RequirementDimension.ALL) {
      workItemService.assess(owner, project.getId(), item.getId(), dimension,
          RequirementDimension.CONFIRMED, "已确认", null, null);
    }
    workItemService.confirmReadiness(reviewer, project.getId(), item.getId());
    SolutionVersion version = solutionService.createVersion(owner, project.getId(), item.getId(),
        "收货扫码处理方案", "初版");
    String business = solutionService.addDesignItem(owner, project.getId(), item.getId(),
        version.getId(), "business", null, "收货扫码流程", businessBody).getId();
    String technical = solutionService.addDesignItem(owner, project.getId(), item.getId(),
        version.getId(), "technical", business, "扫码服务改造",
        "扫码接口增加幂等校验。").getId();
    String criterion = solutionService.addCriterion(owner, project.getId(), item.getId(),
        version.getId(), business, "重复扫码被拦截且当天闭环").getId();
    String task = solutionService.addTask(owner, project.getId(), item.getId(), version.getId(),
        technical, criterion, "实现扫码重复校验", "在扫码接口增加幂等判断",
        "重复扫码返回可读提示", null).getId();
    solutionService.submit(owner, project.getId(), item.getId(), version.getId());
    solutionService.approve(reviewer, project.getId(), item.getId(), version.getId(), "同意");
    return new Fixture(owner, reviewer, project, repositoryPath, repository.getId(),
        baseline.getId(), baselineCommit, item, version.getId(), task, criterion);
  }

  private String advanceRepository(Fixture fixture) throws Exception {
    Files.writeString(fixture.repositoryPath().resolve("src/App.java"),
        "class App { int duplicateCheck; }");
    Files.writeString(fixture.repositoryPath().resolve("src/ScanValidator.java"),
        "class ScanValidator {}");
    GitFixtures.git(fixture.repositoryPath(), "add", "-A");
    GitFixtures.git(fixture.repositoryPath(), "commit", "-m", "implementation");
    return GitFixtures.head(fixture.repositoryPath());
  }

  @Test
  void handoffRequiresAnApprovedSolutionAndContainsBaseline() throws Exception {
    Fixture fixture = approvedFixture();

    DeliveryService.Generated handoff = deliveryService.handoff(fixture.owner(),
        fixture.project().getId(), fixture.item().getId());
    assertThat(handoff.solutionVersionId()).isEqualTo(fixture.solutionVersionId());
    assertThat(handoff.baselineId()).isEqualTo(fixture.baselineId());
    assertThat(handoff.markdown()).contains("开发交接包");
    assertThat(handoff.markdown()).contains(fixture.item().getItemNo());
    assertThat(handoff.markdown()).contains(fixture.baselineId());
    assertThat(handoff.markdown()).contains("实现扫码重复校验");
    assertThat(handoff.markdown()).contains("重复扫码被拦截且当天闭环");

    WorkItem draft = workItemService.create(fixture.owner(), fixture.project().getId(),
        "没有批准方案的需求", "草稿", "low", null);
    assertThatThrownBy(() -> deliveryService.handoff(fixture.owner(), fixture.project().getId(),
        draft.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("approved solution");
  }

  @Test
  void handoffPackageContainsManifestAndIsAudited() throws Exception {
    Fixture fixture = approvedFixture();

    HandoffPackageService.PackageView view = handoffPackageService.export(fixture.owner(),
        fixture.project().getId(), fixture.item().getId());
    assertThat(view.artifactId()).isNotBlank();
    assertThat(view.sha256()).hasSize(64);
    assertThat(view.downloadUrl()).contains("/artifacts/" + view.artifactId() + "/content");

    ArtifactRecord record = artifactService.require(fixture.project().getId(), view.artifactId());
    assertThat(record.getKind()).isEqualTo("handoff_package");
    assertThat(record.getContentHash()).isEqualTo(view.sha256());
    byte[] bytes = artifactService.content(record);

    Map<String, byte[]> entries = new HashMap<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes),
        StandardCharsets.UTF_8)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        entries.put(entry.getName(), zip.readAllBytes());
      }
    }
    assertThat(entries).containsKeys("manifest.properties", "solution-business.md",
        "solution-technical.md", "tasks.md", "acceptance.md", "baseline.md", "handoff.md",
        "README.md", "SECURITY.md");
    assertThat(entries).doesNotContainKey(".env");
    assertThat(new String(entries.get("SECURITY.md"), StandardCharsets.UTF_8))
        .contains("不包含生产凭据");

    Properties manifest = new Properties();
    manifest.load(new java.io.StringReader(
        new String(entries.get("manifest.properties"), StandardCharsets.UTF_8)));
    assertThat(manifest.getProperty("formatVersion")).isEqualTo("1");
    assertThat(manifest.getProperty("itemNo")).isEqualTo(fixture.item().getItemNo());
    assertThat(manifest.getProperty("solutionVersionId")).isEqualTo(fixture.solutionVersionId());
    assertThat(manifest.getProperty("baselineId")).isEqualTo(fixture.baselineId());
    int count = Integer.parseInt(manifest.getProperty("fileCount"));
    for (int index = 0; index < count; index++) {
      String name = manifest.getProperty("file." + index + ".name");
      String expected = manifest.getProperty("file." + index + ".sha256");
      assertThat(entries).containsKey(name);
      assertThat(sha256(entries.get(name))).isEqualTo(expected);
    }

    assertThat(auditLogRepository.findByObjectTypeAndObjectIdOrderByAtAsc("work_item",
        fixture.item().getId()))
        .extracting(log -> log.getAction())
        .contains("handoff.generate", "handoff.export");
  }

  @Test
  void handoffPackageRejectsPossibleSecrets() throws Exception {
    Fixture fixture = approvedFixture("连接串示例 password=supersecret-value");

    assertThatThrownBy(() -> handoffPackageService.export(fixture.owner(),
        fixture.project().getId(), fixture.item().getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("possible secret");
  }

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(
          java.security.MessageDigest.getInstance("SHA-256").digest(value));
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  @Test
  void executionRecordDrivesAcceptanceMatrixAndDeviations() throws Exception {
    Fixture fixture = approvedFixture();
    String to = advanceRepository(fixture);

    var record = executionService.record(fixture.owner(), fixture.project().getId(),
        fixture.item().getId(), fixture.repositoryId(), fixture.baselineId(), to, "实现完成",
        "mvn verify 通过", List.of(fixture.taskId()));
    assertThat(record.getFromCommit()).isEqualTo(fixture.baselineCommit());
    assertThat(record.getToCommit()).isEqualTo(to);
    assertThat(record.isDiverged()).isFalse();

    ExecutionService.Detail detail = executionService.detail(fixture.owner(),
        fixture.project().getId(), fixture.item().getId());
    assertThat(detail.records()).hasSize(1);
    assertThat(detail.changes()).anyMatch(change -> change.getFilePath().equals("src/App.java")
        && change.getChangeType().equals("M"));
    assertThat(detail.changes()).anyMatch(change -> change.getFilePath()
        .equals("src/ScanValidator.java") && change.getChangeType().equals("A"));

    ExecutionService.Analysis analysis = executionService.analysis(fixture.owner(),
        fixture.project().getId(), fixture.item().getId());
    assertThat(analysis.covered()).isEqualTo(1);
    assertThat(analysis.unverified()).isZero();
    assertThat(analysis.matrix()).singleElement().satisfies(row -> {
      assertThat(row.coverage()).isEqualTo("covered");
      assertThat(row.doneTasks()).isEqualTo(1);
      assertThat(row.totalTasks()).isEqualTo(1);
      assertThat(row.goal()).isNotBlank();
      assertThat(row.executionRecordIds()).contains(record.getId());
      assertThat(row.changeCount()).isEqualTo(2);
      assertThat(row.evidenceRefs()).isEmpty();
      assertThat(row.deviationIds()).isEmpty();
    });
    assertThat(analysis.missingTasks()).isEmpty();

    var deviation = executionService.addDeviation(
        fixture.owner(), fixture.project().getId(), fixture.item().getId(), record.getId(), "extra",
        "task", fixture.taskId(), "额外增加了扫码日志");
    ExecutionService.Analysis withDeviation = executionService.analysis(fixture.owner(),
        fixture.project().getId(), fixture.item().getId());
    assertThat(withDeviation.deviations()).hasSize(1);
    assertThat(withDeviation.deviations().get(0).getDeviationType()).isEqualTo("extra");
    assertThat(withDeviation.matrix()).singleElement()
        .satisfies(row -> assertThat(row.deviationIds()).contains(deviation.getId()));

    assertThatThrownBy(() -> executionService.record(fixture.owner(), fixture.project().getId(),
        fixture.item().getId(), fixture.repositoryId(), fixture.baselineId(), "not-a-commit",
        null, null, List.of()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("invalid commit id");

    AppUser viewer = user("viewer");
    projectService.addMember(fixture.owner(), fixture.project().getId(), viewer.getId(),
        AuthorizationService.VIEWER);
    assertThatThrownBy(() -> executionService.record(viewer, fixture.project().getId(),
        fixture.item().getId(), fixture.repositoryId(), fixture.baselineId(), to, null, null,
        List.of()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cannot perform this action");
  }

  @Test
  void deliverySnapshotAndKnowledgeProposalCompleteTheLoop() throws Exception {
    Fixture fixture = approvedFixture();
    String to = advanceRepository(fixture);
    executionService.record(fixture.owner(), fixture.project().getId(), fixture.item().getId(),
        fixture.repositoryId(), fixture.baselineId(), to, "实现完成", "测试通过",
        List.of(fixture.taskId()));

    DeliverySnapshot snapshot = deliveryService.createSnapshot(fixture.owner(),
        fixture.project().getId(), fixture.item().getId(), "As-built v1");
    assertThat(snapshot.getStatus()).isEqualTo(DeliverySnapshot.DRAFT);
    assertThat(snapshot.getBodyMarkdown()).contains("As-built");
    assertThat(snapshot.getBodyMarkdown()).contains("验收矩阵");
    assertThat(snapshot.getBodyMarkdown()).contains(fixture.baselineId());

    assertThatThrownBy(() -> deliveryService.approveSnapshot(fixture.owner(),
        fixture.project().getId(), fixture.item().getId(), snapshot.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cannot approve their own");

    DeliverySnapshot approved = deliveryService.approveSnapshot(fixture.reviewer(),
        fixture.project().getId(), fixture.item().getId(), snapshot.getId());
    assertThat(approved.getStatus()).isEqualTo(DeliverySnapshot.APPROVED);
    assertThat(approved.getApprovedBy()).isEqualTo(fixture.reviewer().getId());
    assertThat(approved.getArtifactId()).isNotBlank();

    assertThatThrownBy(() -> deliveryService.approveSnapshot(fixture.reviewer(),
        fixture.project().getId(), fixture.item().getId(), snapshot.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("only draft delivery snapshots");

    KnowledgeUpdateProposal proposal = deliveryService.createProposal(fixture.owner(),
        fixture.project().getId(), fixture.item().getId(), snapshot.getId(),
        "交付经验：扫码重复入库处理");
    assertThat(proposal.getStatus()).isEqualTo(KnowledgeUpdateProposal.DRAFT);

    assertThatThrownBy(() -> deliveryService.acceptProposal(fixture.owner(),
        fixture.project().getId(), fixture.item().getId(), proposal.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cannot accept their own");

    KnowledgeUpdateProposal accepted = deliveryService.acceptProposal(fixture.reviewer(),
        fixture.project().getId(), fixture.item().getId(), proposal.getId());
    assertThat(accepted.getStatus()).isEqualTo(KnowledgeUpdateProposal.ACCEPTED);
    assertThat(accepted.getKnowledgeDocumentId()).isNotBlank();

    KnowledgeDocument document = knowledgeService.requireDocument(fixture.project().getId(),
        accepted.getKnowledgeDocumentId());
    assertThat(document.getStatus()).isEqualTo(KnowledgeDocument.DRAFT);
    assertThat(document.getApplicableBaselineId()).isEqualTo(fixture.baselineId());
    assertThat(stalenessService.isStale(document)).isFalse();

    ProjectBaseline newer = baselineService.capture(fixture.owner(), fixture.project().getId(),
        "after implementation", false).baseline();
    assertThat(newer.getId()).isNotEqualTo(fixture.baselineId());
    assertThat(stalenessService.isStale(document)).isTrue();
    assertThat(knowledgeService.requireDocument(fixture.project().getId(),
        document.getId()).getStatus()).isEqualTo(KnowledgeDocument.DRAFT);

    // Publish the delivery knowledge through the normal four-eyes flow, then supersede an older doc.
    knowledgeService.submitForReview(fixture.owner(), fixture.project().getId(), document.getId());
    KnowledgeDocument published = knowledgeService.verify(fixture.reviewer(),
        fixture.project().getId(), document.getId());
    assertThat(published.getStatus()).isEqualTo(KnowledgeDocument.VERIFIED);
    assertThat(stalenessService.isStale(published)).isTrue();

    KnowledgeDocument older = knowledgeService.create(fixture.owner(), fixture.project().getId(),
        "delivery", "收货扫码旧知识", "旧版本：收货扫码重复入库由人工处理。",
        List.of(new KnowledgeService.SourceRefInput(
            knowledgeService.sourceRefs(published.getId()).get(0).getArtifactId(), null, null)));
    knowledgeService.submitForReview(fixture.owner(), fixture.project().getId(), older.getId());
    knowledgeService.verify(fixture.reviewer(), fixture.project().getId(), older.getId());
    KnowledgeDocument superseded = knowledgeService.supersede(fixture.reviewer(),
        fixture.project().getId(), older.getId(), published.getId());
    assertThat(superseded.getStatus()).isEqualTo(KnowledgeDocument.SUPERSEDED);
    assertThat(superseded.getSupersededBy()).isEqualTo(published.getId());
    assertThat(searchAdapter.search(fixture.project().getId(), "收货扫码", 10))
        .isNotEmpty()
        .allSatisfy(hit -> assertThat(hit.documentId()).isEqualTo(published.getId()));

    knowledgeService.deprecate(fixture.reviewer(), fixture.project().getId(), published.getId(),
        "演示：标记废弃");
    assertThat(searchAdapter.search(fixture.project().getId(), "收货扫码", 10)).isEmpty();
    KnowledgeUpdateProposal rejected = deliveryService.createProposal(fixture.owner(),
        fixture.project().getId(), fixture.item().getId(), snapshot.getId(), "重复提议");
    assertThat(deliveryService.rejectProposal(fixture.reviewer(), fixture.project().getId(),
        fixture.item().getId(), rejected.getId()).getStatus())
        .isEqualTo(KnowledgeUpdateProposal.REJECTED);

    DeliverySnapshot draft = deliveryService.createSnapshot(fixture.owner(),
        fixture.project().getId(), fixture.item().getId(), "As-built v2 draft");
    assertThatThrownBy(() -> deliveryService.createProposal(fixture.owner(),
        fixture.project().getId(), fixture.item().getId(), draft.getId(), "未批准快照"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("approved delivery snapshot");
  }
}
