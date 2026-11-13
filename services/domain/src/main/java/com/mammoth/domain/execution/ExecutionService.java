package com.mammoth.domain.execution;

import com.mammoth.domain.baseline.BaselineService;
import com.mammoth.domain.baseline.ProjectBaseline;
import com.mammoth.domain.baseline.ProjectBaselineRepo;
import com.mammoth.domain.baseline.ProjectBaselineRepoRepository;
import com.mammoth.domain.baseline.ProjectBaselineRepository;
import com.mammoth.domain.baseline.ProjectRepository;
import com.mammoth.domain.baseline.ProjectRepositoryRepository;
import com.mammoth.domain.external.ExternalRecord;
import com.mammoth.domain.external.ExternalRecordRepository;
import com.mammoth.domain.git.GitAdapter;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.service.AuditService;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.solution.AcceptanceCriterion;
import com.mammoth.domain.solution.AcceptanceCriterionRepository;
import com.mammoth.domain.solution.SolutionDesignItem;
import com.mammoth.domain.solution.SolutionDesignItemRepository;
import com.mammoth.domain.solution.SolutionConsistency;
import com.mammoth.domain.solution.SolutionTask;
import com.mammoth.domain.solution.SolutionTaskRepository;
import com.mammoth.domain.solution.SolutionVersion;
import com.mammoth.domain.solution.SolutionVersionRepository;
import com.mammoth.domain.support.Ids;
import com.mammoth.domain.support.JsonLite;
import com.mammoth.domain.web.ApiException;
import com.mammoth.domain.workitem.WorkItem;
import com.mammoth.domain.workitem.WorkItemService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation recovery (docs/07 §8, ADR-0007): records the observed commit range per repository,
 * file-level changes, task completion and human deviation classification, then derives the
 * acceptance matrix from the currently approved solution.
 */
@Service
public class ExecutionService {

  private static final Set<String> WRITE_ROLES = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR);
  private static final Set<String> ANY_ROLES = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR,
      AuthorizationService.REVIEWER, AuthorizationService.VIEWER);

  private final WorkItemService workItemService;
  private final SolutionVersionRepository versionRepository;
  private final SolutionTaskRepository solutionTaskRepository;
  private final AcceptanceCriterionRepository criterionRepository;
  private final SolutionDesignItemRepository designItemRepository;
  private final ExecutionRecordRepository recordRepository;
  private final ExecutionChangeRepository changeRepository;
  private final ExecutionTaskRepository executionTaskRepository;
  private final ExecutionDeviationRepository deviationRepository;
  private final ProjectRepositoryRepository projectRepositoryRepository;
  private final ProjectBaselineRepository baselineRepository;
  private final ProjectBaselineRepoRepository baselineRepoRepository;
  private final BaselineService baselineService;
  private final GitAdapter gitAdapter;
  private final AuthorizationService authorizationService;
  private final AuditService auditService;
  private final ExternalRecordRepository externalRecordRepository;

  public ExecutionService(WorkItemService workItemService,
      SolutionVersionRepository versionRepository,
      SolutionTaskRepository solutionTaskRepository,
      AcceptanceCriterionRepository criterionRepository,
      SolutionDesignItemRepository designItemRepository,
      ExecutionRecordRepository recordRepository,
      ExecutionChangeRepository changeRepository,
      ExecutionTaskRepository executionTaskRepository,
      ExecutionDeviationRepository deviationRepository,
      ProjectRepositoryRepository projectRepositoryRepository,
      ProjectBaselineRepository baselineRepository,
      ProjectBaselineRepoRepository baselineRepoRepository,
      BaselineService baselineService,
      GitAdapter gitAdapter,
      AuthorizationService authorizationService,
      AuditService auditService,
      ExternalRecordRepository externalRecordRepository) {
    this.workItemService = workItemService;
    this.versionRepository = versionRepository;
    this.solutionTaskRepository = solutionTaskRepository;
    this.criterionRepository = criterionRepository;
    this.designItemRepository = designItemRepository;
    this.recordRepository = recordRepository;
    this.changeRepository = changeRepository;
    this.executionTaskRepository = executionTaskRepository;
    this.deviationRepository = deviationRepository;
    this.projectRepositoryRepository = projectRepositoryRepository;
    this.baselineRepository = baselineRepository;
    this.baselineRepoRepository = baselineRepoRepository;
    this.baselineService = baselineService;
    this.gitAdapter = gitAdapter;
    this.authorizationService = authorizationService;
    this.auditService = auditService;
    this.externalRecordRepository = externalRecordRepository;
  }

  public record Detail(List<ExecutionRecord> records, List<ExecutionChange> changes,
      List<ExecutionTask> taskStates, List<ExecutionDeviation> deviations) {
  }

  public record EvidenceRef(String externalRecordId, String externalKey, String sourceUrl) {
  }

  public record MatrixRow(String criterionId, String criterionText, String designItemId,
      String designItemTitle, String goal, String coverage, int totalTasks, int doneTasks,
      int partialTasks, List<String> executionRecordIds, int changeCount,
      List<EvidenceRef> evidenceRefs, List<String> deviationIds) {
  }

  public record MissingTask(String taskId, String title, String status) {
  }

  public record Analysis(String solutionVersionId, List<MatrixRow> matrix,
      List<MissingTask> missingTasks, List<ExecutionDeviation> deviations,
      List<ExecutionRecord> records, List<EvidenceRef> workItemEvidenceRefs, int covered,
      int partial, int unverified) {
  }

  @Transactional
  public ExecutionRecord record(AppUser actor, String projectId, String workItemId,
      String repositoryId, String baselineId, String toCommit, String summary, String testsNote,
      List<String> completedTaskIds) {
    authorizationService.requireProjectRole(actor, projectId, WRITE_ROLES);
    WorkItem item = workItemService.requireItem(projectId, workItemId);
    String versionId = requireApprovedVersion(item);
    ProjectRepository repository = projectRepositoryRepository
        .findByIdAndProjectId(repositoryId, projectId)
        .orElseThrow(() -> ApiException.notFound("repository not found: " + repositoryId));
    if (!repository.isActive()) {
      throw ApiException.conflict("repository is inactive: " + repository.getLabel());
    }
    ProjectBaseline baseline = baselineRepository.findByIdAndProjectId(baselineId, projectId)
        .orElseThrow(() -> ApiException.notFound("baseline not found: " + baselineId));
    ProjectBaselineRepo baselineRepo = baselineRepoRepository
        .findByBaselineIdAndRepositoryId(baseline.getId(), repository.getId())
        .orElseThrow(() -> ApiException.conflict(
            "baseline " + baseline.getId() + " does not include repository "
                + repository.getLabel()));
    String from = baselineRepo.getCommitSha();
    String to = requireText(toCommit, "toCommit");
    Path repositoryPath = Path.of(repository.getAbsolutePath());
    gitAdapter.verifyCommit(repositoryPath, to);
    boolean diverged = !gitAdapter.isAncestor(repositoryPath, from, to);
    List<GitAdapter.FileChange> changes = gitAdapter.diff(repositoryPath, from, to);

    String now = Ids.nowIso();
    ExecutionRecord record = new ExecutionRecord(Ids.newId(), projectId, workItemId, versionId,
        baseline.getId(), repository.getId(), from, to, diverged, blankToNull(summary),
        blankToNull(testsNote), actor.getId(), now);
    recordRepository.save(record);
    for (GitAdapter.FileChange change : changes) {
      changeRepository.save(new ExecutionChange(Ids.newId(), record.getId(), change.path(),
          change.changeType(), change.oldPath(), change.additions(), change.deletions()));
    }
    if (completedTaskIds != null) {
      for (String taskId : completedTaskIds) {
        if (taskId == null || taskId.isBlank()) {
          continue;
        }
        solutionTaskRepository.findByIdAndSolutionVersionId(taskId, versionId)
            .orElseThrow(() -> ApiException.badRequest(
                "task " + taskId + " does not belong to the approved solution version"));
        executionTaskRepository.save(new ExecutionTask(Ids.newId(), record.getId(), taskId,
            ExecutionTask.DONE, null));
      }
    }
    auditService.record(actor.getId(), "execution.record", "execution_record", record.getId(),
        JsonLite.write(JsonLite.object(
            "repository", repository.getLabel(),
            "from", from,
            "to", to,
            "changes", changes.size(),
            "diverged", diverged,
            "completedTasks", completedTaskIds == null ? 0 : completedTaskIds.size())));
    return record;
  }

  @Transactional(readOnly = true)
  public Detail detail(AppUser actor, String projectId, String workItemId) {
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLES);
    workItemService.requireItem(projectId, workItemId);
    List<ExecutionRecord> records = recordRepository.findByWorkItemIdOrderByRecordedAtAsc(workItemId);
    List<String> ids = records.stream().map(ExecutionRecord::getId).toList();
    List<ExecutionChange> changes = ids.isEmpty()
        ? List.of() : changeRepository.findByExecutionRecordIdInOrderByFilePathAsc(ids);
    List<ExecutionTask> taskStates = ids.isEmpty()
        ? List.of() : executionTaskRepository.findByExecutionRecordIdIn(ids);
    List<ExecutionDeviation> deviations = ids.isEmpty()
        ? List.of() : deviationRepository.findByExecutionRecordIdInOrderByCreatedAtAsc(ids);
    return new Detail(records, changes, taskStates, deviations);
  }

  @Transactional
  public ExecutionDeviation addDeviation(AppUser actor, String projectId, String workItemId,
      String executionRecordId, String deviationType, String targetType, String targetId,
      String description) {
    authorizationService.requireProjectRole(actor, projectId, WRITE_ROLES);
    workItemService.requireItem(projectId, workItemId);
    ExecutionRecord record = recordRepository.findByIdAndProjectId(executionRecordId, projectId)
        .orElseThrow(() -> ApiException.notFound("execution record not found: " + executionRecordId));
    if (!record.getWorkItemId().equals(workItemId)) {
      throw ApiException.notFound("execution record not found for this requirement: "
          + executionRecordId);
    }
    String normalizedTargetType = blankToNull(targetType);
    String normalizedTargetId = blankToNull(targetId);
    if ((normalizedTargetType == null) != (normalizedTargetId == null)) {
      throw ApiException.badRequest("targetType and targetId must be provided together");
    }
    if (normalizedTargetType != null) {
      ExecutionDeviation.normalizeTargetType(normalizedTargetType);
      requireTarget(record.getSolutionVersionId(), normalizedTargetType, normalizedTargetId);
    }
    ExecutionDeviation deviation = new ExecutionDeviation(Ids.newId(), record.getId(),
        deviationType, normalizedTargetType, normalizedTargetId,
        requireText(description, "description"), actor.getId(), Ids.nowIso());
    deviationRepository.save(deviation);
    auditService.record(actor.getId(), "execution.deviation", "execution_record", record.getId(),
        JsonLite.write(JsonLite.object("type", deviation.getDeviationType())));
    return deviation;
  }

  @Transactional(readOnly = true)
  public Analysis analysis(AppUser actor, String projectId, String workItemId) {
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLES);
    WorkItem item = workItemService.requireItem(projectId, workItemId);
    String versionId = requireApprovedVersion(item);
    List<SolutionTask> tasks = solutionTaskRepository
        .findBySolutionVersionIdOrderByOrdinalAsc(versionId);
    List<AcceptanceCriterion> criteria = criterionRepository
        .findBySolutionVersionIdOrderByOrdinalAsc(versionId);
    List<SolutionDesignItem> items = designItemRepository
        .findBySolutionVersionIdOrderByOrdinalAsc(versionId);
    List<ExecutionRecord> records = recordRepository
        .findByWorkItemIdAndSolutionVersionIdOrderByRecordedAtAsc(workItemId, versionId);
    List<String> recordIds = records.stream().map(ExecutionRecord::getId).toList();
    List<ExecutionTask> taskStates = recordIds.isEmpty()
        ? List.of() : executionTaskRepository.findByExecutionRecordIdIn(recordIds);
    List<ExecutionDeviation> deviations = recordIds.isEmpty()
        ? List.of() : deviationRepository.findByExecutionRecordIdInOrderByCreatedAtAsc(recordIds);

    Map<String, String> statusByTask = aggregateTaskStatus(taskStates);
    Map<String, String> designTitles = new HashMap<>();
    Map<String, SolutionDesignItem> designById = new HashMap<>();
    for (SolutionDesignItem designItem : items) {
      designTitles.put(designItem.getId(), designItem.getTitle());
      designById.put(designItem.getId(), designItem);
    }
    Map<String, Set<String>> recordIdsByTask = new HashMap<>();
    for (ExecutionTask state : taskStates) {
      recordIdsByTask.computeIfAbsent(state.getTaskId(), ignored -> new LinkedHashSet<>())
          .add(state.getExecutionRecordId());
    }
    Map<String, Integer> changeCountByRecord = new HashMap<>();
    if (!records.isEmpty()) {
      for (ExecutionChange change : changeRepository.findByExecutionRecordIdInOrderByFilePathAsc(
          records.stream().map(ExecutionRecord::getId).toList())) {
        changeCountByRecord.merge(change.getExecutionRecordId(), 1, Integer::sum);
      }
    }
    List<EvidenceRef> workItemEvidenceRefs = externalRecordRepository
        .findByProjectIdAndMappedWorkItemIdAndMappedCriterionIdIsNullAndStatus(
            projectId, workItemId, ExternalRecord.STATUS_MAPPED)
        .stream().map(ExecutionService::evidenceRef).toList();

    List<MatrixRow> matrix = new ArrayList<>();
    int covered = 0;
    int partial = 0;
    int unverified = 0;
    for (AcceptanceCriterion criterion : criteria) {
      List<SolutionTask> linked = tasks.stream()
          .filter(task -> criterion.getId().equals(task.getAcceptanceCriterionId())
              || (task.getDesignItemId() != null
                  && task.getDesignItemId().equals(criterion.getDesignItemId())))
          .toList();
      int done = 0;
      int partialCount = 0;
      for (SolutionTask task : linked) {
        String status = statusByTask.get(task.getId());
        if (ExecutionTask.DONE.equals(status)) {
          done++;
        } else if (ExecutionTask.PARTIAL.equals(status)) {
          partialCount++;
        }
      }
      String coverage;
      if (linked.isEmpty() || (done == 0 && partialCount == 0)) {
        coverage = "unverified";
        unverified++;
      } else if (done == linked.size()) {
        coverage = "covered";
        covered++;
      } else {
        coverage = "partial";
        partial++;
      }
      Set<String> executionRecordIds = new LinkedHashSet<>();
      Set<String> linkedTaskIds = new LinkedHashSet<>();
      for (SolutionTask task : linked) {
        linkedTaskIds.add(task.getId());
        executionRecordIds.addAll(recordIdsByTask.getOrDefault(task.getId(), Set.of()));
      }
      int changeCount = executionRecordIds.stream()
          .mapToInt(recordId -> changeCountByRecord.getOrDefault(recordId, 0)).sum();
      SolutionDesignItem designItem = designById.get(criterion.getDesignItemId());
      String goal = rootGoal(designItem, designById);
      Set<String> subtree = designItem == null ? Set.of()
          : SolutionConsistency.subtreeIds(designItem, items);
      List<EvidenceRef> evidenceRefs = externalRecordRepository
          .findByProjectIdAndMappedCriterionIdAndStatus(projectId, criterion.getId(),
              ExternalRecord.STATUS_MAPPED)
          .stream().map(ExecutionService::evidenceRef).toList();
      List<String> deviationIds = deviations.stream()
          .filter(deviation -> matchesCriterion(deviation, criterion.getId(), linkedTaskIds,
              subtree))
          .map(ExecutionDeviation::getId).toList();
      matrix.add(new MatrixRow(criterion.getId(), criterion.getText(), criterion.getDesignItemId(),
          designTitles.getOrDefault(criterion.getDesignItemId(), ""), goal, coverage, linked.size(),
          done, partialCount, List.copyOf(executionRecordIds), changeCount, evidenceRefs,
          deviationIds));
    }

    List<MissingTask> missingTasks = new ArrayList<>();
    for (SolutionTask task : tasks) {
      String status = statusByTask.get(task.getId());
      if (!ExecutionTask.DONE.equals(status)) {
        missingTasks.add(new MissingTask(task.getId(), task.getTitle(),
            status == null ? "missing" : status));
      }
    }

    return new Analysis(versionId, matrix, missingTasks, deviations, records,
        workItemEvidenceRefs, covered, partial, unverified);
  }

  private static EvidenceRef evidenceRef(ExternalRecord record) {
    return new EvidenceRef(record.getId(), record.getExternalKey(), record.getSourceUrlSanitized());
  }

  private static boolean matchesCriterion(ExecutionDeviation deviation, String criterionId,
      Set<String> linkedTaskIds, Set<String> designSubtree) {
    if (deviation.getTargetType() == null || deviation.getTargetId() == null) {
      return false;
    }
    return switch (deviation.getTargetType()) {
      case ExecutionDeviation.TARGET_CRITERION -> criterionId.equals(deviation.getTargetId());
      case ExecutionDeviation.TARGET_TASK -> linkedTaskIds.contains(deviation.getTargetId());
      case ExecutionDeviation.TARGET_DESIGN_ITEM -> designSubtree.contains(deviation.getTargetId());
      default -> false;
    };
  }

  private static String rootGoal(SolutionDesignItem designItem,
      Map<String, SolutionDesignItem> designById) {
    SolutionDesignItem current = designItem;
    SolutionDesignItem nearestBusiness = null;
    while (current != null) {
      if (SolutionDesignItem.BUSINESS.equals(current.getItemKind())) {
        if (current.getParentId() == null) {
          return current.getTitle();
        }
        if (nearestBusiness == null) {
          nearestBusiness = current;
        }
      }
      current = current.getParentId() == null ? null : designById.get(current.getParentId());
    }
    return nearestBusiness == null ? "" : nearestBusiness.getTitle();
  }

  /** The latest state per task wins; done beats partial beats skipped. */
  private static Map<String, String> aggregateTaskStatus(List<ExecutionTask> taskStates) {
    Map<String, String> result = new LinkedHashMap<>();
    for (ExecutionTask state : taskStates) {
      String previous = result.get(state.getTaskId());
      result.put(state.getTaskId(), stronger(previous, state.getStatus()));
    }
    return result;
  }

  private static String stronger(String previous, String current) {
    if (previous == null) {
      return current;
    }
    if (ExecutionTask.DONE.equals(previous)) {
      return previous;
    }
    if (ExecutionTask.DONE.equals(current)) {
      return current;
    }
    if (ExecutionTask.PARTIAL.equals(previous)) {
      return previous;
    }
    return current;
  }

  private void requireTarget(String versionId, String targetType, String targetId) {
    switch (targetType) {
      case ExecutionDeviation.TARGET_TASK -> solutionTaskRepository
          .findByIdAndSolutionVersionId(targetId, versionId)
          .orElseThrow(() -> ApiException.badRequest("task not found in the approved solution: "
              + targetId));
      case ExecutionDeviation.TARGET_CRITERION -> criterionRepository
          .findByIdAndSolutionVersionId(targetId, versionId)
          .orElseThrow(() -> ApiException.badRequest(
              "acceptance criterion not found in the approved solution: " + targetId));
      case ExecutionDeviation.TARGET_DESIGN_ITEM -> designItemRepository
          .findByIdAndSolutionVersionId(targetId, versionId)
          .orElseThrow(() -> ApiException.badRequest(
              "design item not found in the approved solution: " + targetId));
      default -> throw ApiException.badRequest("unsupported deviation target type: " + targetType);
    }
  }

  private String requireApprovedVersion(WorkItem item) {
    String versionId = item.getApprovedSolutionVersionId();
    if (versionId == null || versionId.isBlank()) {
      throw ApiException.conflict(
          "an approved solution is required before recording implementation evidence");
    }
    versionRepository.findByIdAndWorkItemId(versionId, item.getId())
        .orElseThrow(() -> ApiException.notFound(
            "approved solution version not found: " + versionId));
    return versionId;
  }

  @Transactional(readOnly = true)
  public ProjectBaseline latestBaseline(String projectId) {
    return baselineService.latest(projectId);
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
