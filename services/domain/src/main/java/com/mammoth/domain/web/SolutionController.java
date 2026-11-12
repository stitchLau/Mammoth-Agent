package com.mammoth.domain.web;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.security.CurrentUser;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.solution.AcceptanceCriterion;
import com.mammoth.domain.solution.ApprovalRecord;
import com.mammoth.domain.solution.SolutionConsistency;
import com.mammoth.domain.solution.SolutionDesignItem;
import com.mammoth.domain.solution.SolutionRenderer;
import com.mammoth.domain.solution.SolutionService;
import com.mammoth.domain.solution.SolutionTask;
import com.mammoth.domain.solution.SolutionVersion;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Unified SolutionModel API (M2): structure, dual-view rendering, review and versioning. */
@RestController
@RequestMapping("/api/projects/{projectId}/work-items/{workItemId}/solutions")
public class SolutionController {

  private final SolutionService solutionService;
  private final AuthorizationService authorizationService;

  public SolutionController(SolutionService solutionService,
      AuthorizationService authorizationService) {
    this.solutionService = solutionService;
    this.authorizationService = authorizationService;
  }

  public record CreateVersionRequest(String title, String changeSummary) {
  }

  public record ApprovalRequest(String comment) {
  }

  public record DesignItemRequest(String itemKind, String parentId, String title, String body) {
  }

  public record CriterionRequest(String designItemId, String text) {
  }

  public record TaskRequest(String designItemId, String acceptanceCriterionId, String title,
      String description, String doneWhen, String dependsOnTaskId) {
  }

  public record VersionResponse(String id, int version, String status, String title,
      String changeSummary, String contextSnapshotId, String createdBy, String submittedAt,
      String approverUserId, String approvedAt, String createdAt, String updatedAt) {

    static VersionResponse from(SolutionVersion version) {
      return new VersionResponse(version.getId(), version.getVersion(), version.getStatus(),
          version.getTitle(), version.getChangeSummary(), version.getContextSnapshotId(),
          version.getCreatedBy(), version.getSubmittedAt(), version.getApproverUserId(),
          version.getApprovedAt(), version.getCreatedAt(), version.getUpdatedAt());
    }
  }

  public record DesignItemResponse(String id, String itemKind, String parentId, String title,
      String body, int ordinal) {

    static DesignItemResponse from(SolutionDesignItem item) {
      return new DesignItemResponse(item.getId(), item.getItemKind(), item.getParentId(),
          item.getTitle(), item.getBody(), item.getOrdinal());
    }
  }

  public record CriterionResponse(String id, String designItemId, String text, int ordinal) {

    static CriterionResponse from(AcceptanceCriterion criterion) {
      return new CriterionResponse(criterion.getId(), criterion.getDesignItemId(),
          criterion.getText(), criterion.getOrdinal());
    }
  }

  public record TaskResponse(String id, String designItemId, String acceptanceCriterionId,
      String title, String description, String doneWhen, String dependsOnTaskId, int ordinal) {

    static TaskResponse from(SolutionTask task) {
      return new TaskResponse(task.getId(), task.getDesignItemId(), task.getAcceptanceCriterionId(),
          task.getTitle(), task.getDescription(), task.getDoneWhen(), task.getDependsOnTaskId(),
          task.getOrdinal());
    }
  }

  public record IssueResponse(String code, String message, String objectId) {
  }

  public record CountsResponse(int businessItems, int technicalItems, int sharedItems,
      int acceptanceCriteria, int tasks, int uncoveredGoals) {
  }

  public record ReportResponse(boolean ok, List<IssueResponse> issues, CountsResponse counts) {

    static ReportResponse from(SolutionConsistency.Report report) {
      return new ReportResponse(report.ok(),
          report.issues().stream()
              .map(issue -> new IssueResponse(issue.code(), issue.message(), issue.objectId()))
              .toList(),
          new CountsResponse(report.counts().businessItems(), report.counts().technicalItems(),
              report.counts().sharedItems(), report.counts().acceptanceCriteria(),
              report.counts().tasks(), report.counts().uncoveredGoals()));
    }
  }

  public record ApprovalResponse(String id, String action, String actorUserId, String comment,
      String at) {

    static ApprovalResponse from(ApprovalRecord record) {
      return new ApprovalResponse(record.getId(), record.getAction(), record.getActorUserId(),
          record.getComment(), record.getAt());
    }
  }

  public record DetailResponse(VersionResponse version, List<DesignItemResponse> designItems,
      List<CriterionResponse> acceptanceCriteria, List<TaskResponse> tasks, ReportResponse report,
      List<ApprovalResponse> approvals) {
  }

  public record TraceRowResponse(String goal, String designItems, String tasks, String criteria) {
  }

  public record RenderResponse(String view, String markdown, int designItemCount, int taskCount,
      int criterionCount, List<TraceRowResponse> traceability) {
  }

  @PostMapping
  public VersionResponse create(@PathVariable String projectId, @PathVariable String workItemId,
      @RequestBody CreateVersionRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return VersionResponse.from(solutionService.createVersion(actor, projectId, workItemId,
        request.title(), request.changeSummary()));
  }

  @GetMapping
  public List<VersionResponse> list(@PathVariable String projectId,
      @PathVariable String workItemId) {
    AppUser actor = CurrentUser.require(authorizationService);
    return solutionService.listVersions(actor, projectId, workItemId).stream()
        .map(VersionResponse::from).toList();
  }

  @GetMapping("/{versionId}")
  public DetailResponse detail(@PathVariable String projectId, @PathVariable String workItemId,
      @PathVariable String versionId) {
    AppUser actor = CurrentUser.require(authorizationService);
    SolutionService.Detail detail = solutionService.detail(actor, projectId, workItemId, versionId);
    return new DetailResponse(VersionResponse.from(detail.version()),
        detail.designItems().stream().map(DesignItemResponse::from).toList(),
        detail.acceptanceCriteria().stream().map(CriterionResponse::from).toList(),
        detail.tasks().stream().map(TaskResponse::from).toList(),
        ReportResponse.from(detail.report()),
        detail.approvals().stream().map(ApprovalResponse::from).toList());
  }

  @PutMapping("/{versionId}")
  public VersionResponse updateMeta(@PathVariable String projectId,
      @PathVariable String workItemId, @PathVariable String versionId,
      @RequestBody CreateVersionRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return VersionResponse.from(solutionService.updateVersionMeta(actor, projectId, workItemId,
        versionId, request.title(), request.changeSummary()));
  }

  @PostMapping("/{versionId}/design-items")
  public DesignItemResponse addDesignItem(@PathVariable String projectId,
      @PathVariable String workItemId, @PathVariable String versionId,
      @RequestBody DesignItemRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return DesignItemResponse.from(solutionService.addDesignItem(actor, projectId, workItemId,
        versionId, request.itemKind(), request.parentId(), request.title(), request.body()));
  }

  @PutMapping("/{versionId}/design-items/{itemId}")
  public DesignItemResponse updateDesignItem(@PathVariable String projectId,
      @PathVariable String workItemId, @PathVariable String versionId, @PathVariable String itemId,
      @RequestBody DesignItemRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return DesignItemResponse.from(solutionService.updateDesignItem(actor, projectId, workItemId,
        versionId, itemId, request.itemKind(), request.title(), request.body()));
  }

  @DeleteMapping("/{versionId}/design-items/{itemId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteDesignItem(@PathVariable String projectId, @PathVariable String workItemId,
      @PathVariable String versionId, @PathVariable String itemId) {
    AppUser actor = CurrentUser.require(authorizationService);
    solutionService.deleteDesignItem(actor, projectId, workItemId, versionId, itemId);
  }

  @PostMapping("/{versionId}/acceptance-criteria")
  public CriterionResponse addCriterion(@PathVariable String projectId,
      @PathVariable String workItemId, @PathVariable String versionId,
      @RequestBody CriterionRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return CriterionResponse.from(solutionService.addCriterion(actor, projectId, workItemId,
        versionId, request.designItemId(), request.text()));
  }

  @DeleteMapping("/{versionId}/acceptance-criteria/{criterionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteCriterion(@PathVariable String projectId, @PathVariable String workItemId,
      @PathVariable String versionId, @PathVariable String criterionId) {
    AppUser actor = CurrentUser.require(authorizationService);
    solutionService.deleteCriterion(actor, projectId, workItemId, versionId, criterionId);
  }

  @PostMapping("/{versionId}/tasks")
  public TaskResponse addTask(@PathVariable String projectId, @PathVariable String workItemId,
      @PathVariable String versionId, @RequestBody TaskRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return TaskResponse.from(solutionService.addTask(actor, projectId, workItemId, versionId,
        request.designItemId(), request.acceptanceCriterionId(), request.title(),
        request.description(), request.doneWhen(), request.dependsOnTaskId()));
  }

  @DeleteMapping("/{versionId}/tasks/{taskId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteTask(@PathVariable String projectId, @PathVariable String workItemId,
      @PathVariable String versionId, @PathVariable String taskId) {
    AppUser actor = CurrentUser.require(authorizationService);
    solutionService.deleteTask(actor, projectId, workItemId, versionId, taskId);
  }

  @GetMapping("/{versionId}/render")
  public RenderResponse render(@PathVariable String projectId, @PathVariable String workItemId,
      @PathVariable String versionId,
      @RequestParam(value = "view", defaultValue = "business") String view) {
    AppUser actor = CurrentUser.require(authorizationService);
    SolutionRenderer.RenderResult result = solutionService.render(actor, projectId, workItemId,
        versionId, view);
    return new RenderResponse(result.view(), result.markdown(), result.designItemCount(),
        result.taskCount(), result.criterionCount(),
        result.traceability().stream()
            .map(row -> new TraceRowResponse(row.goal(), row.designItems(), row.tasks(),
                row.criteria()))
            .toList());
  }

  @PostMapping("/{versionId}/submit")
  public VersionResponse submit(@PathVariable String projectId, @PathVariable String workItemId,
      @PathVariable String versionId) {
    AppUser actor = CurrentUser.require(authorizationService);
    return VersionResponse.from(
        solutionService.submit(actor, projectId, workItemId, versionId));
  }

  @PostMapping("/{versionId}/approve")
  public VersionResponse approve(@PathVariable String projectId, @PathVariable String workItemId,
      @PathVariable String versionId, @RequestBody(required = false) ApprovalRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return VersionResponse.from(solutionService.approve(actor, projectId, workItemId, versionId,
        request == null ? null : request.comment()));
  }

  @PostMapping("/{versionId}/request-changes")
  public VersionResponse requestChanges(@PathVariable String projectId,
      @PathVariable String workItemId, @PathVariable String versionId,
      @RequestBody ApprovalRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return VersionResponse.from(solutionService.requestChanges(actor, projectId, workItemId,
        versionId, request.comment()));
  }

  @PostMapping("/{versionId}/new-version")
  public VersionResponse newVersion(@PathVariable String projectId,
      @PathVariable String workItemId, @PathVariable String versionId,
      @RequestBody(required = false) CreateVersionRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return VersionResponse.from(solutionService.newVersion(actor, projectId, workItemId, versionId,
        request == null ? null : request.changeSummary()));
  }
}
