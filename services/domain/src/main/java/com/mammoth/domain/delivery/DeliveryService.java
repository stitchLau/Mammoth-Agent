package com.mammoth.domain.delivery;

import com.mammoth.domain.artifact.ArtifactRecord;
import com.mammoth.domain.artifact.ArtifactService;
import com.mammoth.domain.baseline.BaselineService;
import com.mammoth.domain.baseline.ProjectBaseline;
import com.mammoth.domain.baseline.ProjectRepository;
import com.mammoth.domain.baseline.ProjectRepositoryRepository;
import com.mammoth.domain.execution.ExecutionChange;
import com.mammoth.domain.execution.ExecutionDeviation;
import com.mammoth.domain.execution.ExecutionRecord;
import com.mammoth.domain.execution.ExecutionService;
import com.mammoth.domain.knowledge.KnowledgeDocument;
import com.mammoth.domain.knowledge.KnowledgeService;
import com.mammoth.domain.notification.NotificationService;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.AppUserRepository;
import com.mammoth.domain.service.AuditService;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.solution.AcceptanceCriterion;
import com.mammoth.domain.solution.SolutionService;
import com.mammoth.domain.solution.SolutionTask;
import com.mammoth.domain.support.Ids;
import com.mammoth.domain.support.JsonLite;
import com.mammoth.domain.web.ApiException;
import com.mammoth.domain.workitem.WorkItem;
import com.mammoth.domain.workitem.WorkItemService;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivery loop (docs/07 §8): As-built snapshot generation, four-eyes approval, and the knowledge
 * update proposal that feeds approved delivery knowledge back as a draft knowledge document.
 */
@Service
public class DeliveryService {

  private static final Set<String> WRITE_ROLES = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR);
  private static final Set<String> DECIDE_ROLES = Set.of(
      AuthorizationService.OWNER, AuthorizationService.REVIEWER);
  private static final Set<String> ANY_ROLES = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR,
      AuthorizationService.REVIEWER, AuthorizationService.VIEWER);

  private final WorkItemService workItemService;
  private final SolutionService solutionService;
  private final ExecutionService executionService;
  private final BaselineService baselineService;
  private final ProjectRepositoryRepository projectRepositoryRepository;
  private final DeliverySnapshotRepository snapshotRepository;
  private final KnowledgeUpdateProposalRepository proposalRepository;
  private final KnowledgeService knowledgeService;
  private final ArtifactService artifactService;
  private final AppUserRepository appUserRepository;
  private final AuthorizationService authorizationService;
  private final AuditService auditService;
  private final NotificationService notificationService;

  public DeliveryService(WorkItemService workItemService, SolutionService solutionService,
      ExecutionService executionService, BaselineService baselineService,
      ProjectRepositoryRepository projectRepositoryRepository,
      DeliverySnapshotRepository snapshotRepository,
      KnowledgeUpdateProposalRepository proposalRepository, KnowledgeService knowledgeService,
      ArtifactService artifactService, AppUserRepository appUserRepository,
      AuthorizationService authorizationService, AuditService auditService,
      NotificationService notificationService) {
    this.workItemService = workItemService;
    this.solutionService = solutionService;
    this.executionService = executionService;
    this.baselineService = baselineService;
    this.projectRepositoryRepository = projectRepositoryRepository;
    this.snapshotRepository = snapshotRepository;
    this.proposalRepository = proposalRepository;
    this.knowledgeService = knowledgeService;
    this.artifactService = artifactService;
    this.appUserRepository = appUserRepository;
    this.authorizationService = authorizationService;
    this.auditService = auditService;
    this.notificationService = notificationService;
  }

  public record Generated(String markdown, String solutionVersionId, int solutionVersion,
      String baselineId, String generatedAt, String workItemId, String itemNo, String itemTitle,
      String solutionTitle, String contextSnapshotId, String businessMarkdown,
      String technicalMarkdown, String tasksMarkdown, String acceptanceMarkdown,
      String baselineMarkdown) {
  }

  @Transactional
  public DeliverySnapshot createSnapshot(AppUser actor, String projectId, String workItemId,
      String title) {
    authorizationService.requireProjectRole(actor, projectId, WRITE_ROLES);
    WorkItem item = workItemService.requireItem(projectId, workItemId);
    String versionId = requireApprovedVersion(item);
    String markdown = buildAsBuilt(actor, projectId, item, versionId);
    ProjectBaseline baseline = baselineService.latest(projectId);
    String now = Ids.nowIso();
    DeliverySnapshot snapshot = new DeliverySnapshot(Ids.newId(), projectId, workItemId, versionId,
        baseline == null ? null : baseline.getId(), requireText(title, "title"), markdown,
        actor.getId(), now);
    snapshotRepository.save(snapshot);
    auditService.record(actor.getId(), "delivery.snapshot_create", "delivery_snapshot",
        snapshot.getId(), JsonLite.write(JsonLite.object(
            "solutionVersionId", versionId,
            "baselineId", snapshot.getBaselineId())));
    return snapshot;
  }

  @Transactional(readOnly = true)
  public List<DeliverySnapshot> listSnapshots(AppUser actor, String projectId, String workItemId) {
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLES);
    workItemService.requireItem(projectId, workItemId);
    return snapshotRepository.findByWorkItemIdOrderByCreatedAtAsc(workItemId);
  }

  @Transactional(readOnly = true)
  public DeliverySnapshot requireSnapshot(AppUser actor, String projectId, String workItemId,
      String snapshotId) {
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLES);
    workItemService.requireItem(projectId, workItemId);
    return snapshotRepository.findByIdAndProjectId(snapshotId, projectId)
        .filter(snapshot -> snapshot.getWorkItemId().equals(workItemId))
        .orElseThrow(() -> ApiException.notFound("delivery snapshot not found: " + snapshotId));
  }

  /**
   * Approve the As-built snapshot: four-eyes (owner/reviewer, not the author). The markdown is
   * written to the immutable Artifact store so the approved delivery is reproducible.
   */
  @Transactional
  public DeliverySnapshot approveSnapshot(AppUser actor, String projectId, String workItemId,
      String snapshotId) {
    authorizationService.requireProjectRole(actor, projectId, DECIDE_ROLES);
    DeliverySnapshot snapshot = requireSnapshot(actor, projectId, workItemId, snapshotId);
    if (!DeliverySnapshot.DRAFT.equals(snapshot.getStatus())) {
      throw ApiException.conflict("only draft delivery snapshots can be approved");
    }
    if (actor.getId().equals(snapshot.getCreatedBy())) {
      throw ApiException.forbidden("the snapshot author cannot approve their own delivery snapshot");
    }
    ArtifactRecord artifact = artifactService.upload(actor, projectId, "delivery",
        "delivery-" + snapshot.getId().substring(0, 8) + ".md", "text/markdown",
        "Approved delivery snapshot " + snapshot.getId(),
        snapshot.getBodyMarkdown().getBytes(StandardCharsets.UTF_8));
    String now = Ids.nowIso();
    snapshot.approve(actor.getId(), artifact.getId(), now);
    snapshotRepository.save(snapshot);
    auditService.record(actor.getId(), "delivery.snapshot_approve", "delivery_snapshot",
        snapshot.getId(), JsonLite.write(JsonLite.object("artifactId", artifact.getId())));
    notificationService.deliveryApproved(actor, projectId, workItemId, snapshot.getId(),
        snapshot.getTitle());
    return snapshot;
  }

  @Transactional
  public KnowledgeUpdateProposal createProposal(AppUser actor, String projectId,
      String workItemId, String snapshotId, String title) {
    authorizationService.requireProjectRole(actor, projectId, WRITE_ROLES);
    DeliverySnapshot snapshot = requireSnapshot(actor, projectId, workItemId, snapshotId);
    if (!DeliverySnapshot.APPROVED.equals(snapshot.getStatus())) {
      throw ApiException.conflict("only an approved delivery snapshot can seed a knowledge proposal");
    }
    String body = snapshot.getBodyMarkdown()
        + "\n## 知识更新建议\n"
        + "- 沉淀本次交付的业务流程、规则与异常处理经验。\n"
        + "- 标注适用代码基线：" + (snapshot.getBaselineId() == null ? "无" : snapshot.getBaselineId()) + "。\n"
        + "- 接受后生成知识草稿，仍需人工审核（四眼）后才可被检索。\n";
    KnowledgeUpdateProposal proposal = new KnowledgeUpdateProposal(Ids.newId(), projectId,
        workItemId, snapshot.getId(), requireText(title, "title"), body, actor.getId(),
        Ids.nowIso());
    proposalRepository.save(proposal);
    auditService.record(actor.getId(), "knowledge_proposal.create", "knowledge_update_proposal",
        proposal.getId(), JsonLite.write(JsonLite.object("snapshotId", snapshot.getId())));
    return proposal;
  }

  @Transactional(readOnly = true)
  public List<KnowledgeUpdateProposal> listProposals(AppUser actor, String projectId,
      String workItemId) {
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLES);
    workItemService.requireItem(projectId, workItemId);
    return proposalRepository.findByWorkItemIdOrderByCreatedAtAsc(workItemId);
  }

  /**
   * Accept a proposal: owner/reviewer, not the proposer. Creates a draft knowledge document authored
   * by the proposer (so the acceptor can later review it as a different person).
   */
  @Transactional
  public KnowledgeUpdateProposal acceptProposal(AppUser actor, String projectId, String workItemId,
      String proposalId) {
    authorizationService.requireProjectRole(actor, projectId, DECIDE_ROLES);
    KnowledgeUpdateProposal proposal = requireProposal(projectId, workItemId, proposalId);
    if (!KnowledgeUpdateProposal.DRAFT.equals(proposal.getStatus())) {
      throw ApiException.conflict("only draft proposals can be accepted");
    }
    if (actor.getId().equals(proposal.getCreatedBy())) {
      throw ApiException.forbidden("the proposer cannot accept their own knowledge proposal");
    }
    DeliverySnapshot snapshot = snapshotRepository
        .findByIdAndProjectId(proposal.getDeliverySnapshotId(), projectId)
        .orElseThrow(() -> ApiException.notFound("delivery snapshot not found for proposal"));
    if (!DeliverySnapshot.APPROVED.equals(snapshot.getStatus())) {
      throw ApiException.conflict("the delivery snapshot must be approved before acceptance");
    }
    if (snapshot.getArtifactId() == null) {
      throw ApiException.conflict("the approved delivery snapshot has no artifact to cite");
    }
    AppUser proposer = appUserRepository.findById(proposal.getCreatedBy())
        .orElseThrow(() -> ApiException.notFound(
            "proposal author no longer exists: " + proposal.getCreatedBy()));
    KnowledgeDocument document = knowledgeService.createDeliveryDraft(proposer, projectId,
        proposal.getTitle(), proposal.getBodyMarkdown(), snapshot.getArtifactId(),
        snapshot.getBaselineId());
    proposal.accept(actor.getId(), document.getId(), Ids.nowIso());
    proposalRepository.save(proposal);
    auditService.record(actor.getId(), "knowledge_proposal.accept", "knowledge_update_proposal",
        proposal.getId(), JsonLite.write(JsonLite.object("knowledgeDocumentId", document.getId())));
    return proposal;
  }

  @Transactional
  public KnowledgeUpdateProposal rejectProposal(AppUser actor, String projectId, String workItemId,
      String proposalId) {
    authorizationService.requireProjectRole(actor, projectId, DECIDE_ROLES);
    KnowledgeUpdateProposal proposal = requireProposal(projectId, workItemId, proposalId);
    if (!KnowledgeUpdateProposal.DRAFT.equals(proposal.getStatus())) {
      throw ApiException.conflict("only draft proposals can be rejected");
    }
    proposal.reject(actor.getId(), Ids.nowIso());
    proposalRepository.save(proposal);
    auditService.record(actor.getId(), "knowledge_proposal.reject", "knowledge_update_proposal",
        proposalId, null);
    return proposal;
  }

  /** Handoff package for the approved solution (S3): Markdown + structured references. */
  @Transactional
  public Generated handoff(AppUser actor, String projectId, String workItemId) {
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLES);
    WorkItem item = workItemService.requireItem(projectId, workItemId);
    String versionId = requireApprovedVersion(item);
    ProjectBaseline baseline = baselineService.latest(projectId);
    String business = solutionService.render(actor, projectId, workItemId, versionId, "business")
        .markdown();
    String technical = solutionService.render(actor, projectId, workItemId, versionId, "technical")
        .markdown();
    SolutionService.Detail detail = solutionService.detail(actor, projectId, workItemId, versionId);
    StringBuilder markdown = new StringBuilder();
    markdown.append("# 开发交接包：").append(item.getItemNo()).append(' ').append(item.getTitle())
        .append("\n\n");
    markdown.append("- 需求状态：").append(item.getStatus()).append('\n');
    markdown.append("- 批准方案：v").append(detail.version().getVersion()).append(' ')
        .append(detail.version().getTitle()).append('\n');
    markdown.append("- 方案批准：").append(detail.version().getApproverUserId()).append(' ')
        .append(detail.version().getApprovedAt()).append('\n');
    markdown.append("- 代码基线：")
        .append(baseline == null ? "未捕获" : baseline.getId() + "（" + baseline.getCapturedAt() + "）")
        .append('\n');
    markdown.append("- 上下文快照：")
        .append(detail.version().getContextSnapshotId() == null ? "无"
            : detail.version().getContextSnapshotId())
        .append("\n\n");
    markdown.append("> 只读约束：实施方不得修改已批准方案；Mammoth 不托管代码，变更以 commit 为准。\n\n");
    String tasksMarkdown = renderTasks(detail.tasks());
    String acceptanceMarkdown = renderAcceptance(detail.acceptanceCriteria());
    String baselineMarkdown = renderBaseline(baseline);
    markdown.append("## 1. 开发任务\n\n").append(tasksMarkdown);
    markdown.append("## 2. 验收标准\n\n").append(acceptanceMarkdown);
    markdown.append('\n').append(business).append('\n').append(technical).append('\n');
    String generatedAt = Ids.nowIso();
    auditService.record(actor.getId(), "handoff.generate", "work_item", workItemId,
        JsonLite.write(JsonLite.object("solutionVersionId", versionId,
            "baselineId", baseline == null ? null : baseline.getId())));
    return new Generated(markdown.toString(), versionId, detail.version().getVersion(),
        baseline == null ? null : baseline.getId(), generatedAt, workItemId, item.getItemNo(),
        item.getTitle(), detail.version().getTitle(), detail.version().getContextSnapshotId(),
        business, technical, tasksMarkdown, acceptanceMarkdown, baselineMarkdown);
  }

  private String renderTasks(List<SolutionTask> tasks) {
    StringBuilder out = new StringBuilder();
    for (SolutionTask task : tasks) {
      out.append("### ").append(task.getTitle()).append("\n\n");
      if (task.getDescription() != null) {
        out.append("- 说明：").append(task.getDescription()).append('\n');
      }
      if (task.getDoneWhen() != null) {
        out.append("- 完成条件：").append(task.getDoneWhen()).append('\n');
      }
      if (task.getDependsOnTaskId() != null) {
        out.append("- 依赖任务：").append(task.getDependsOnTaskId()).append('\n');
      }
      out.append('\n');
    }
    return out.toString();
  }

  private String renderAcceptance(List<AcceptanceCriterion> criteria) {
    StringBuilder out = new StringBuilder();
    for (AcceptanceCriterion criterion : criteria) {
      out.append("- ").append(criterion.getText()).append('\n');
    }
    return out.toString();
  }

  private String renderBaseline(ProjectBaseline baseline) {
    if (baseline == null) {
      return "# 代码基线\n\n未捕获基线。\n";
    }
    Map<String, String> labels = new HashMap<>();
    for (ProjectRepository repository : projectRepositoryRepository
        .findByProjectIdOrderByAddedAtAsc(baseline.getProjectId())) {
      labels.put(repository.getId(), repository.getLabel());
    }
    StringBuilder out = new StringBuilder("# 代码基线\n\n");
    out.append("- 基线 ID：").append(baseline.getId()).append('\n');
    out.append("- 捕获时间：").append(baseline.getCapturedAt()).append('\n');
    out.append("- 捕获人：").append(baseline.getCapturedBy()).append('\n');
    out.append("- 完整：").append(baseline.isComplete()).append('\n');
    out.append("- 说明：").append(baseline.getNote() == null ? "—" : baseline.getNote())
        .append("\n\n");
    out.append("| 仓库 | commit | branch | dirty | worktree |\n|---|---|---|---|---|\n");
    for (var repo : baselineService.detail(baseline.getId(), baseline.getProjectId()).repos()) {
      out.append("| ").append(labels.getOrDefault(repo.getRepositoryId(), repo.getRepositoryId()))
          .append(" | `").append(repo.getCommitSha()).append("` | ")
          .append(repo.getBranch() == null ? "—" : repo.getBranch()).append(" | ")
          .append(repo.isDirty()).append(" | ").append(repo.isWorktree()).append(" |\n");
    }
    return out.toString();
  }

  private String buildAsBuilt(AppUser actor, String projectId, WorkItem item, String versionId) {
    ProjectBaseline baseline = baselineService.latest(projectId);
    SolutionService.Detail solution = solutionService.detail(actor, projectId, item.getId(),
        versionId);
    ExecutionService.Detail execution = executionService.detail(actor, projectId, item.getId());
    ExecutionService.Analysis analysis = executionService.analysis(actor, projectId, item.getId());
    Map<String, String> repositoryLabels = new HashMap<>();
    for (ProjectRepository repository : projectRepositoryRepository
        .findByProjectIdOrderByAddedAtAsc(projectId)) {
      repositoryLabels.put(repository.getId(), repository.getLabel());
    }
    Map<String, String> changesByRecord = new HashMap<>();
    for (ExecutionChange change : execution.changes()) {
      changesByRecord.merge(change.getExecutionRecordId(),
          "| " + change.getChangeType() + " | `" + change.getFilePath() + "` | +"
              + change.getAdditions() + "/-" + change.getDeletions() + " |\n",
          String::concat);
    }

    StringBuilder markdown = new StringBuilder();
    markdown.append("# As-built 交付快照：").append(item.getItemNo()).append(' ')
        .append(item.getTitle()).append("\n\n");
    markdown.append("- 批准方案：v").append(solution.version().getVersion()).append(' ')
        .append(solution.version().getTitle()).append('\n');
    markdown.append("- 代码基线：")
        .append(baseline == null ? "未捕获" : baseline.getId()).append('\n');
    markdown.append("- 生成人：").append(actor.getId()).append("，生成时间：").append(Ids.nowIso())
        .append("\n\n");
    markdown.append("## 1. 批准的业务/技术方案\n\n");
    markdown.append(solutionService.render(actor, projectId, item.getId(), versionId, "business")
        .markdown()).append('\n');
    markdown.append(solutionService.render(actor, projectId, item.getId(), versionId, "technical")
        .markdown()).append('\n');
    markdown.append("## 2. 实施记录\n\n");
    if (execution.records().isEmpty()) {
      markdown.append("（尚未记录实施证据）\n");
    }
    for (ExecutionRecord record : execution.records()) {
      markdown.append("### ").append(repositoryLabels.getOrDefault(record.getRepositoryId(),
          record.getRepositoryId())).append(' ').append(record.getFromCommit(), 0, 8)
          .append(" → ").append(record.getToCommit(), 0, 8);
      if (record.isDiverged()) {
        markdown.append("（diverged）");
      }
      markdown.append("\n\n");
      if (record.getSummary() != null) {
        markdown.append(record.getSummary()).append("\n\n");
      }
      markdown.append("| 类型 | 文件 | 增/删 |\n|---|---|---|\n");
      markdown.append(changesByRecord.getOrDefault(record.getId(), ""));
      markdown.append('\n');
    }
    markdown.append("## 3. 偏差\n\n");
    if (execution.deviations().isEmpty()) {
      markdown.append("（无异动记录）\n");
    }
    for (ExecutionDeviation deviation : execution.deviations()) {
      markdown.append("- [").append(deviation.getDeviationType()).append("] ")
          .append(deviation.getDescription()).append('\n');
    }
    markdown.append("\n## 4. 验收矩阵\n\n");
    markdown.append("| 验收标准 | 覆盖 | 任务(完成/总数) |\n|---|---|---|\n");
    for (ExecutionService.MatrixRow row : analysis.matrix()) {
      markdown.append("| ").append(row.criterionText()).append(" | ").append(row.coverage())
          .append(" | ").append(row.doneTasks()).append('/').append(row.totalTasks())
          .append(" |\n");
    }
    markdown.append("\n## 5. 未完成任务候选\n\n");
    if (analysis.missingTasks().isEmpty()) {
      markdown.append("（无）\n");
    }
    for (ExecutionService.MissingTask task : analysis.missingTasks()) {
      markdown.append("- ").append(task.title()).append("（").append(task.status()).append("）\n");
    }
    return markdown.toString();
  }

  private KnowledgeUpdateProposal requireProposal(String projectId, String workItemId,
      String proposalId) {
    return proposalRepository.findByIdAndWorkItemId(proposalId, workItemId)
        .filter(proposal -> proposal.getProjectId().equals(projectId))
        .orElseThrow(() -> ApiException.notFound("knowledge proposal not found: " + proposalId));
  }

  private String requireApprovedVersion(WorkItem item) {
    String versionId = item.getApprovedSolutionVersionId();
    if (versionId == null || versionId.isBlank()) {
      throw ApiException.conflict(
          "an approved solution is required before delivery or handoff");
    }
    return versionId;
  }

  private static String requireText(String value, String field) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isEmpty()) {
      throw ApiException.badRequest(field + " must not be empty");
    }
    return normalized;
  }
}
