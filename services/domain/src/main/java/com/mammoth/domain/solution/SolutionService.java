package com.mammoth.domain.solution;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.service.AuditService;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.support.Ids;
import com.mammoth.domain.support.JsonLite;
import com.mammoth.domain.web.ApiException;
import com.mammoth.domain.workitem.ContextSnapshotService;
import com.mammoth.domain.workitem.RequirementSnapshot;
import com.mammoth.domain.workitem.WorkItem;
import com.mammoth.domain.workitem.WorkItemService;
import com.mammoth.domain.workitem.WorkItemStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unified SolutionModel lifecycle (docs/03 §6, docs/07 §5–6): structure editing, consistency gate,
 * dual-view rendering, review, approval and versioning. Approved versions are immutable.
 */
@Service
public class SolutionService {

  private static final Set<String> ANY_ROLES = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR,
      AuthorizationService.REVIEWER, AuthorizationService.VIEWER);
  private static final Set<String> WRITE_ROLES = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR);
  private static final Set<String> DECIDE_ROLES = Set.of(
      AuthorizationService.OWNER, AuthorizationService.REVIEWER);

  private final WorkItemService workItemService;
  private final SolutionVersionRepository versionRepository;
  private final SolutionDesignItemRepository designItemRepository;
  private final AcceptanceCriterionRepository criterionRepository;
  private final SolutionTaskRepository taskRepository;
  private final ApprovalRecordRepository approvalRepository;
  private final ContextSnapshotService snapshotService;
  private final SolutionRenderer renderer;
  private final AuthorizationService authorizationService;
  private final AuditService auditService;

  public SolutionService(WorkItemService workItemService,
      SolutionVersionRepository versionRepository,
      SolutionDesignItemRepository designItemRepository,
      AcceptanceCriterionRepository criterionRepository,
      SolutionTaskRepository taskRepository,
      ApprovalRecordRepository approvalRepository,
      ContextSnapshotService snapshotService,
      SolutionRenderer renderer,
      AuthorizationService authorizationService,
      AuditService auditService) {
    this.workItemService = workItemService;
    this.versionRepository = versionRepository;
    this.designItemRepository = designItemRepository;
    this.criterionRepository = criterionRepository;
    this.taskRepository = taskRepository;
    this.approvalRepository = approvalRepository;
    this.snapshotService = snapshotService;
    this.renderer = renderer;
    this.authorizationService = authorizationService;
    this.auditService = auditService;
  }

  public record Detail(SolutionVersion version, List<SolutionDesignItem> designItems,
      List<AcceptanceCriterion> acceptanceCriteria, List<SolutionTask> tasks,
      SolutionConsistency.Report report, List<ApprovalRecord> approvals) {
  }

  @Transactional
  public SolutionVersion createVersion(AppUser actor, String projectId, String workItemId,
      String title, String changeSummary) {
    authorizationService.requireProjectRole(actor, projectId, WRITE_ROLES);
    WorkItem item = workItemService.requireItem(projectId, workItemId);
    if (!WorkItemStatus.READY_FOR_ANALYSIS.equals(item.getStatus())
        && !WorkItemStatus.ANALYZING.equals(item.getStatus())) {
      throw ApiException.conflict(
          "a solution can only be started from ready_for_analysis or analyzing (current: "
              + item.getStatus() + ")");
    }
    if (WorkItemStatus.READY_FOR_ANALYSIS.equals(item.getStatus())) {
      workItemService.transitionFromCommand(actor, item, WorkItemStatus.ANALYZING,
          "solution.create");
    }
    int nextVersion = versionRepository.findTopByWorkItemIdOrderByVersionDesc(workItemId)
        .map(existing -> existing.getVersion() + 1)
        .orElse(1);
    SolutionVersion version = new SolutionVersion(Ids.newId(), workItemId, nextVersion,
        requireText(title, "title"), blankToNull(changeSummary), item.getContextSnapshotId(),
        actor.getId(), Ids.nowIso());
    versionRepository.save(version);
    auditService.record(actor.getId(), "solution.create", "solution_version", version.getId(),
        JsonLite.write(JsonLite.object("workItemId", workItemId, "version", nextVersion)));
    return version;
  }

  @Transactional(readOnly = true)
  public List<SolutionVersion> listVersions(AppUser actor, String projectId, String workItemId) {
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLES);
    workItemService.requireItem(projectId, workItemId);
    return versionRepository.findByWorkItemIdOrderByVersionAsc(workItemId);
  }

  @Transactional(readOnly = true)
  public Detail detail(AppUser actor, String projectId, String workItemId, String versionId) {
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLES);
    workItemService.requireItem(projectId, workItemId);
    SolutionVersion version = requireVersion(workItemId, versionId);
    List<SolutionDesignItem> items = designItemRepository
        .findBySolutionVersionIdOrderByOrdinalAsc(versionId);
    List<AcceptanceCriterion> criteria = criterionRepository
        .findBySolutionVersionIdOrderByOrdinalAsc(versionId);
    List<SolutionTask> tasks = taskRepository.findBySolutionVersionIdOrderByOrdinalAsc(versionId);
    return new Detail(version, items, criteria, tasks,
        SolutionConsistency.validate(items, criteria, tasks),
        approvalRepository.findByObjectTypeAndObjectIdOrderByAtAsc(
            ApprovalRecord.OBJECT_SOLUTION_VERSION, versionId));
  }

  @Transactional
  public SolutionVersion updateVersionMeta(AppUser actor, String projectId, String workItemId,
      String versionId, String title, String changeSummary) {
    requireWrite(actor, projectId);
    workItemService.requireItem(projectId, workItemId);
    SolutionVersion version = requireVersion(workItemId, versionId);
    requireDraft(version);
    version.updateDraft(requireText(title, "title"), blankToNull(changeSummary), Ids.nowIso());
    versionRepository.save(version);
    auditService.record(actor.getId(), "solution.update", "solution_version", versionId, null);
    return version;
  }

  @Transactional
  public SolutionDesignItem addDesignItem(AppUser actor, String projectId, String workItemId,
      String versionId, String itemKind, String parentId, String title, String body) {
    requireWrite(actor, projectId);
    workItemService.requireItem(projectId, workItemId);
    SolutionVersion version = requireVersion(workItemId, versionId);
    requireDraft(version);
    List<SolutionDesignItem> existing = designItemRepository
        .findBySolutionVersionIdOrderByOrdinalAsc(versionId);
    String normalizedParentId = blankToNull(parentId);
    if (normalizedParentId != null) {
      SolutionDesignItem parent = requireDesignItem(versionId, normalizedParentId);
      if (SolutionConsistency.depth(parent, index(existing)) >= SolutionConsistency.MAX_DEPTH) {
        throw ApiException.conflict(
            "design item nesting is limited to " + SolutionConsistency.MAX_DEPTH + " levels");
      }
    }
    SolutionDesignItem item = new SolutionDesignItem(Ids.newId(), versionId,
        SolutionDesignItem.normalizeKind(itemKind), normalizedParentId,
        requireText(title, "title"), body == null ? "" : body.trim(), existing.size() + 1);
    designItemRepository.save(item);
    auditService.record(actor.getId(), "solution.design_item.add", "solution_version", versionId,
        JsonLite.write(JsonLite.object("itemId", item.getId(), "kind", item.getItemKind())));
    return item;
  }

  @Transactional
  public SolutionDesignItem updateDesignItem(AppUser actor, String projectId, String workItemId,
      String versionId, String itemId, String itemKind, String title, String body) {
    requireWrite(actor, projectId);
    workItemService.requireItem(projectId, workItemId);
    SolutionVersion version = requireVersion(workItemId, versionId);
    requireDraft(version);
    SolutionDesignItem item = requireDesignItem(versionId, itemId);
    item.update(SolutionDesignItem.normalizeKind(itemKind), requireText(title, "title"),
        body == null ? "" : body.trim());
    designItemRepository.save(item);
    auditService.record(actor.getId(), "solution.design_item.update", "solution_version", versionId,
        JsonLite.write(JsonLite.object("itemId", itemId)));
    return item;
  }

  @Transactional
  public void deleteDesignItem(AppUser actor, String projectId, String workItemId, String versionId,
      String itemId) {
    requireWrite(actor, projectId);
    workItemService.requireItem(projectId, workItemId);
    SolutionVersion version = requireVersion(workItemId, versionId);
    requireDraft(version);
    requireDesignItem(versionId, itemId);
    if (designItemRepository.countByParentId(itemId) > 0) {
      throw ApiException.conflict("design item has child items; delete the children first");
    }
    if (criterionRepository.countByDesignItemId(itemId) > 0) {
      throw ApiException.conflict("design item is referenced by acceptance criteria");
    }
    if (taskRepository.countByDesignItemId(itemId) > 0) {
      throw ApiException.conflict("design item is referenced by tasks");
    }
    designItemRepository.deleteById(itemId);
    auditService.record(actor.getId(), "solution.design_item.delete", "solution_version", versionId,
        JsonLite.write(JsonLite.object("itemId", itemId)));
  }

  @Transactional
  public AcceptanceCriterion addCriterion(AppUser actor, String projectId, String workItemId,
      String versionId, String designItemId, String text) {
    requireWrite(actor, projectId);
    workItemService.requireItem(projectId, workItemId);
    SolutionVersion version = requireVersion(workItemId, versionId);
    requireDraft(version);
    String normalizedDesignItemId = requireText(designItemId, "design item");
    requireDesignItem(versionId, normalizedDesignItemId);
    List<AcceptanceCriterion> existing = criterionRepository
        .findBySolutionVersionIdOrderByOrdinalAsc(versionId);
    AcceptanceCriterion criterion = new AcceptanceCriterion(Ids.newId(), versionId,
        normalizedDesignItemId, requireText(text, "acceptance criterion text"),
        existing.size() + 1);
    criterionRepository.save(criterion);
    auditService.record(actor.getId(), "solution.criterion.add", "solution_version", versionId,
        JsonLite.write(JsonLite.object("criterionId", criterion.getId())));
    return criterion;
  }

  @Transactional
  public void deleteCriterion(AppUser actor, String projectId, String workItemId, String versionId,
      String criterionId) {
    requireWrite(actor, projectId);
    workItemService.requireItem(projectId, workItemId);
    SolutionVersion version = requireVersion(workItemId, versionId);
    requireDraft(version);
    AcceptanceCriterion criterion = criterionRepository
        .findByIdAndSolutionVersionId(criterionId, versionId)
        .orElseThrow(() -> ApiException.notFound("acceptance criterion not found: " + criterionId));
    if (taskRepository.countByAcceptanceCriterionId(criterionId) > 0) {
      throw ApiException.conflict("acceptance criterion is referenced by tasks");
    }
    criterionRepository.delete(criterion);
    auditService.record(actor.getId(), "solution.criterion.delete", "solution_version", versionId,
        JsonLite.write(JsonLite.object("criterionId", criterionId)));
  }

  @Transactional
  public SolutionTask addTask(AppUser actor, String projectId, String workItemId, String versionId,
      String designItemId, String acceptanceCriterionId, String title, String description,
      String doneWhen, String dependsOnTaskId) {
    requireWrite(actor, projectId);
    workItemService.requireItem(projectId, workItemId);
    SolutionVersion version = requireVersion(workItemId, versionId);
    requireDraft(version);
    String normalizedDesignItemId = blankToNull(designItemId);
    String normalizedCriterionId = blankToNull(acceptanceCriterionId);
    if (normalizedDesignItemId == null && normalizedCriterionId == null) {
      throw ApiException.badRequest("a task must trace to a design item or an acceptance criterion");
    }
    if (normalizedDesignItemId != null) {
      requireDesignItem(versionId, normalizedDesignItemId);
    }
    if (normalizedCriterionId != null) {
      requireCriterion(versionId, normalizedCriterionId);
    }
    String normalizedDependsOn = blankToNull(dependsOnTaskId);
    if (normalizedDependsOn != null) {
      requireTask(versionId, normalizedDependsOn);
    }
    List<SolutionTask> existing = taskRepository.findBySolutionVersionIdOrderByOrdinalAsc(versionId);
    SolutionTask task = new SolutionTask(Ids.newId(), versionId, normalizedDesignItemId,
        normalizedCriterionId, requireText(title, "title"), blankToNull(description),
        blankToNull(doneWhen), normalizedDependsOn, existing.size() + 1);
    taskRepository.save(task);
    auditService.record(actor.getId(), "solution.task.add", "solution_version", versionId,
        JsonLite.write(JsonLite.object("taskId", task.getId())));
    return task;
  }

  @Transactional
  public void deleteTask(AppUser actor, String projectId, String workItemId, String versionId,
      String taskId) {
    requireWrite(actor, projectId);
    workItemService.requireItem(projectId, workItemId);
    SolutionVersion version = requireVersion(workItemId, versionId);
    requireDraft(version);
    SolutionTask task = requireTask(versionId, taskId);
    if (taskRepository.countByDependsOnTaskId(taskId) > 0) {
      throw ApiException.conflict("task is a dependency of another task");
    }
    taskRepository.delete(task);
    auditService.record(actor.getId(), "solution.task.delete", "solution_version", versionId,
        JsonLite.write(JsonLite.object("taskId", taskId)));
  }

  @Transactional(readOnly = true)
  public SolutionRenderer.RenderResult render(AppUser actor, String projectId, String workItemId,
      String versionId, String view) {
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLES);
    WorkItem item = workItemService.requireItem(projectId, workItemId);
    SolutionVersion version = requireVersion(workItemId, versionId);
    RequirementSnapshot snapshot = version.getContextSnapshotId() == null
        ? null
        : snapshotService.require(version.getContextSnapshotId());
    return renderer.render(item, version, snapshot,
        designItemRepository.findBySolutionVersionIdOrderByOrdinalAsc(versionId),
        criterionRepository.findBySolutionVersionIdOrderByOrdinalAsc(versionId),
        taskRepository.findBySolutionVersionIdOrderByOrdinalAsc(versionId), view);
  }

  @Transactional
  public SolutionVersion submit(AppUser actor, String projectId, String workItemId,
      String versionId) {
    requireWrite(actor, projectId);
    WorkItem item = workItemService.requireItem(projectId, workItemId);
    SolutionVersion version = requireVersion(workItemId, versionId);
    requireDraft(version);
    if (!WorkItemStatus.ANALYZING.equals(item.getStatus())) {
      throw ApiException.conflict(
          "solution can only be submitted while the requirement is analyzing (current: "
              + item.getStatus() + ")");
    }
    List<SolutionDesignItem> items = designItemRepository
        .findBySolutionVersionIdOrderByOrdinalAsc(versionId);
    List<AcceptanceCriterion> criteria = criterionRepository
        .findBySolutionVersionIdOrderByOrdinalAsc(versionId);
    List<SolutionTask> tasks = taskRepository.findBySolutionVersionIdOrderByOrdinalAsc(versionId);
    SolutionConsistency.Report report = SolutionConsistency.validate(items, criteria, tasks);
    if (!report.ok()) {
      throw ApiException.conflict("solution is not consistent: " + joinIssues(report));
    }
    RequirementSnapshot snapshot = workItemService.captureSnapshot(actor, item, "solution_submit");
    String now = Ids.nowIso();
    version.submit(snapshot.getId(), now);
    versionRepository.save(version);
    workItemService.transitionFromCommand(actor, item, WorkItemStatus.SOLUTION_REVIEW,
        "solution.submit");
    recordApproval(actor, versionId, ApprovalRecord.ACTION_SUBMIT, null);
    auditService.record(actor.getId(), "solution.submit", "solution_version", versionId,
        JsonLite.write(JsonLite.object("snapshotId", snapshot.getId())));
    return version;
  }

  @Transactional
  public SolutionVersion approve(AppUser actor, String projectId, String workItemId,
      String versionId, String comment) {
    requireDecide(actor, projectId);
    WorkItem item = workItemService.requireItem(projectId, workItemId);
    SolutionVersion version = requireVersion(workItemId, versionId);
    if (!SolutionVersion.IN_REVIEW.equals(version.getStatus())) {
      throw ApiException.conflict("only solutions in review can be approved");
    }
    if (actor.getId().equals(version.getCreatedBy())) {
      throw ApiException.forbidden("the solution author cannot approve their own solution");
    }
    String now = Ids.nowIso();
    for (SolutionVersion other : versionRepository.findByWorkItemIdAndStatus(workItemId,
        SolutionVersion.APPROVED)) {
      other.supersede(now);
      versionRepository.save(other);
    }
    version.approve(actor.getId(), now);
    versionRepository.save(version);
    workItemService.transitionFromCommand(actor, item, WorkItemStatus.APPROVED, "solution.approve");
    item.attachApprovedSolution(version.getId());
    recordApproval(actor, versionId, ApprovalRecord.ACTION_APPROVE, blankToNull(comment));
    auditService.record(actor.getId(), "solution.approve", "solution_version", versionId,
        JsonLite.write(JsonLite.object("version", version.getVersion())));
    return version;
  }

  @Transactional
  public SolutionVersion requestChanges(AppUser actor, String projectId, String workItemId,
      String versionId, String comment) {
    requireDecide(actor, projectId);
    WorkItem item = workItemService.requireItem(projectId, workItemId);
    SolutionVersion version = requireVersion(workItemId, versionId);
    if (!SolutionVersion.IN_REVIEW.equals(version.getStatus())) {
      throw ApiException.conflict("only solutions in review can be sent back for changes");
    }
    String normalizedComment = blankToNull(comment);
    if (normalizedComment == null) {
      throw ApiException.badRequest("requesting changes requires a comment");
    }
    version.requestChanges(Ids.nowIso());
    versionRepository.save(version);
    workItemService.transitionFromCommand(actor, item, WorkItemStatus.ANALYZING,
        "solution.request_changes");
    recordApproval(actor, versionId, ApprovalRecord.ACTION_REQUEST_CHANGES, normalizedComment);
    auditService.record(actor.getId(), "solution.request_changes", "solution_version", versionId,
        JsonLite.write(JsonLite.object("comment", normalizedComment)));
    return version;
  }

  /**
   * Create the next draft version from an approved/superseded version (AC-5). The approved version
   * stays untouched; the requirement reopens to {@code analyzing} until the new version is approved.
   */
  @Transactional
  public SolutionVersion newVersion(AppUser actor, String projectId, String workItemId,
      String versionId, String changeSummary) {
    requireWrite(actor, projectId);
    WorkItem item = workItemService.requireItem(projectId, workItemId);
    SolutionVersion source = requireVersion(workItemId, versionId);
    if (SolutionVersion.DRAFT.equals(source.getStatus())) {
      throw ApiException.conflict("edit the existing draft instead of creating a new version");
    }
    if (SolutionVersion.IN_REVIEW.equals(source.getStatus())) {
      throw ApiException.conflict("request changes first; a version in review cannot be forked");
    }
    String now = Ids.nowIso();
    if (!WorkItemStatus.ANALYZING.equals(item.getStatus())) {
      workItemService.transitionFromCommand(actor, item, WorkItemStatus.ANALYZING,
          "solution.new_version");
    }
    int nextVersion = versionRepository.findTopByWorkItemIdOrderByVersionDesc(workItemId)
        .map(existing -> existing.getVersion() + 1)
        .orElse(1);
    SolutionVersion version = new SolutionVersion(Ids.newId(), workItemId, nextVersion,
        source.getTitle(),
        changeSummary == null || changeSummary.isBlank()
            ? "修订自 v" + source.getVersion()
            : changeSummary.trim(),
        item.getContextSnapshotId(), actor.getId(), now);
    versionRepository.save(version);
    copyStructure(source.getId(), version.getId());
    auditService.record(actor.getId(), "solution.new_version", "solution_version", version.getId(),
        JsonLite.write(JsonLite.object("fromVersion", source.getVersion(), "toVersion", nextVersion)));
    return version;
  }

  private void copyStructure(String sourceVersionId, String targetVersionId) {
    Map<String, String> itemIdMap = new HashMap<>();
    for (SolutionDesignItem entry : designItemRepository
        .findBySolutionVersionIdOrderByOrdinalAsc(sourceVersionId)) {
      String newParentId = entry.getParentId() == null ? null : itemIdMap.get(entry.getParentId());
      SolutionDesignItem copy = new SolutionDesignItem(Ids.newId(), targetVersionId,
          entry.getItemKind(), newParentId, entry.getTitle(), entry.getBody(), entry.getOrdinal());
      designItemRepository.save(copy);
      itemIdMap.put(entry.getId(), copy.getId());
    }
    Map<String, String> criterionIdMap = new HashMap<>();
    for (AcceptanceCriterion criterion : criterionRepository
        .findBySolutionVersionIdOrderByOrdinalAsc(sourceVersionId)) {
      String designItemId = itemIdMap.get(criterion.getDesignItemId());
      if (designItemId == null) {
        throw ApiException.conflict("source acceptance criterion references a missing design item");
      }
      AcceptanceCriterion copy = new AcceptanceCriterion(Ids.newId(), targetVersionId, designItemId,
          criterion.getText(), criterion.getOrdinal());
      criterionRepository.save(copy);
      criterionIdMap.put(criterion.getId(), copy.getId());
    }
    Map<String, String> taskIdMap = new HashMap<>();
    for (SolutionTask task : taskRepository
        .findBySolutionVersionIdOrderByOrdinalAsc(sourceVersionId)) {
      String designItemId = task.getDesignItemId() == null
          ? null : itemIdMap.get(task.getDesignItemId());
      String criterionId = task.getAcceptanceCriterionId() == null
          ? null : criterionIdMap.get(task.getAcceptanceCriterionId());
      String dependsOn = task.getDependsOnTaskId() == null
          ? null : taskIdMap.get(task.getDependsOnTaskId());
      SolutionTask copy = new SolutionTask(Ids.newId(), targetVersionId, designItemId, criterionId,
          task.getTitle(), task.getDescription(), task.getDoneWhen(), dependsOn, task.getOrdinal());
      taskRepository.save(copy);
      taskIdMap.put(task.getId(), copy.getId());
    }
  }

  private void recordApproval(AppUser actor, String versionId, String action, String comment) {
    approvalRepository.save(new ApprovalRecord(Ids.newId(),
        ApprovalRecord.OBJECT_SOLUTION_VERSION, versionId, action, actor.getId(), comment,
        Ids.nowIso()));
  }

  private void requireWrite(AppUser actor, String projectId) {
    authorizationService.requireProjectRole(actor, projectId, WRITE_ROLES);
  }

  private void requireDecide(AppUser actor, String projectId) {
    authorizationService.requireProjectRole(actor, projectId, DECIDE_ROLES);
  }

  private SolutionVersion requireVersion(String workItemId, String versionId) {
    return versionRepository.findByIdAndWorkItemId(versionId, workItemId)
        .orElseThrow(() -> ApiException.notFound("solution version not found: " + versionId));
  }

  private void requireDraft(SolutionVersion version) {
    if (!SolutionVersion.DRAFT.equals(version.getStatus())) {
      throw ApiException.conflict(
          "solution version is " + version.getStatus() + " and can no longer be edited");
    }
  }

  private SolutionDesignItem requireDesignItem(String versionId, String itemId) {
    return designItemRepository.findByIdAndSolutionVersionId(itemId, versionId)
        .orElseThrow(() -> ApiException.notFound(
            "design item not found in this solution version: " + itemId));
  }

  private AcceptanceCriterion requireCriterion(String versionId, String criterionId) {
    return criterionRepository.findByIdAndSolutionVersionId(criterionId, versionId)
        .orElseThrow(() -> ApiException.notFound(
            "acceptance criterion not found in this solution version: " + criterionId));
  }

  private SolutionTask requireTask(String versionId, String taskId) {
    return taskRepository.findByIdAndSolutionVersionId(taskId, versionId)
        .orElseThrow(() -> ApiException.notFound(
            "task not found in this solution version: " + taskId));
  }

  private static Map<String, SolutionDesignItem> index(List<SolutionDesignItem> items) {
    Map<String, SolutionDesignItem> byId = new HashMap<>();
    for (SolutionDesignItem item : items) {
      byId.put(item.getId(), item);
    }
    return byId;
  }

  private static String joinIssues(SolutionConsistency.Report report) {
    List<String> messages = new ArrayList<>();
    for (SolutionConsistency.Issue issue : report.issues()) {
      messages.add(issue.code() + ": " + issue.message());
    }
    return String.join("; ", messages);
  }

  private static String requireText(String value, String field) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isEmpty()) {
      throw ApiException.badRequest(field + " must not be empty");
    }
    return normalized;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
