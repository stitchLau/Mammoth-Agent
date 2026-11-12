package com.mammoth.domain.web;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.security.CurrentUser;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.solution.SolutionVersion;
import com.mammoth.domain.workitem.ReadinessService;
import com.mammoth.domain.workitem.RequirementAssessment;
import com.mammoth.domain.workitem.RequirementEvidence;
import com.mammoth.domain.workitem.RequirementSnapshot;
import com.mammoth.domain.workitem.WorkItem;
import com.mammoth.domain.workitem.WorkItemService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Requirement work item API (M2): evidence batches, readiness gate and guarded transitions. */
@RestController
@RequestMapping("/api/projects/{projectId}/work-items")
public class WorkItemController {

  private final WorkItemService workItemService;
  private final AuthorizationService authorizationService;

  public WorkItemController(WorkItemService workItemService,
      AuthorizationService authorizationService) {
    this.workItemService = workItemService;
    this.authorizationService = authorizationService;
  }

  public record CreateWorkItemRequest(String title, String summary, String priority,
      String sourceNote) {
  }

  public record UpdateWorkItemRequest(String title, String summary, String priority,
      String ownerUserId) {
  }

  public record AddEvidenceRequest(String evidenceType, String text, String artifactId,
      String locator, String capturedAt) {
  }

  public record AssessmentRequest(String state, String note, String assumptionNote,
      String decisionOwner) {
  }

  public record TransitionRequest(String target, String comment) {
  }

  public record WorkItemResponse(String id, String projectId, String itemType, String itemNo,
      String title, String summary, String status, String priority, String sourceNote,
      String ownerUserId, String createdBy, String priorStatus, String readyConfirmedBy,
      String readyConfirmedAt, String contextSnapshotId, String approvedSolutionVersionId,
      String createdAt, String updatedAt) {

    static WorkItemResponse from(WorkItem item) {
      return new WorkItemResponse(item.getId(), item.getProjectId(), item.getItemType(),
          item.getItemNo(), item.getTitle(), item.getSummary(), item.getStatus(), item.getPriority(),
          item.getSourceNote(), item.getOwnerUserId(), item.getCreatedBy(), item.getPriorStatus(),
          item.getReadyConfirmedBy(), item.getReadyConfirmedAt(), item.getContextSnapshotId(),
          item.getApprovedSolutionVersionId(), item.getCreatedAt(), item.getUpdatedAt());
    }
  }

  public record EvidenceResponse(String id, int batchNo, String evidenceType, String text,
      String artifactId, String locator, String capturedAt, String addedBy, String createdAt) {

    static EvidenceResponse from(RequirementEvidence evidence) {
      return new EvidenceResponse(evidence.getId(), evidence.getBatchNo(),
          evidence.getEvidenceType(), evidence.getText(), evidence.getArtifactId(),
          evidence.getLocator(), evidence.getCapturedAt(), evidence.getAddedBy(),
          evidence.getCreatedAt());
    }
  }

  public record AssessmentResponse(String dimension, boolean critical, String state, String note,
      String assumptionNote, String decisionOwner, String updatedBy, String updatedAt) {
  }

  public record ReadinessResponse(String workItemId, boolean ready, List<String> blocking,
      List<String> warnings, int catalogVersion, String confirmedBy, String confirmedAt,
      String snapshotId, List<AssessmentResponse> dimensions) {

    static ReadinessResponse from(ReadinessService.ReadinessSummary summary) {
      return new ReadinessResponse(summary.workItemId(), summary.ready(), summary.blocking(),
          summary.warnings(), summary.catalogVersion(), summary.confirmedBy(),
          summary.confirmedAt(), summary.snapshotId(),
          summary.dimensions().stream()
              .map(dimension -> new AssessmentResponse(dimension.dimension(), dimension.critical(),
                  dimension.state(), dimension.note(), dimension.assumptionNote(),
                  dimension.decisionOwner(), dimension.updatedBy(), dimension.updatedAt()))
              .toList());
    }
  }

  public record SnapshotResponse(String id, int version, String payloadJson, String createdBy,
      String createdAt) {

    static SnapshotResponse from(RequirementSnapshot snapshot) {
      return new SnapshotResponse(snapshot.getId(), snapshot.getVersion(), snapshot.getPayloadJson(),
          snapshot.getCreatedBy(), snapshot.getCreatedAt());
    }
  }

  public record SolutionVersionResponse(String id, int version, String status, String title,
      String changeSummary, String contextSnapshotId, String createdBy, String submittedAt,
      String approverUserId, String approvedAt, String createdAt, String updatedAt) {

    static SolutionVersionResponse from(SolutionVersion version) {
      return new SolutionVersionResponse(version.getId(), version.getVersion(), version.getStatus(),
          version.getTitle(), version.getChangeSummary(), version.getContextSnapshotId(),
          version.getCreatedBy(), version.getSubmittedAt(), version.getApproverUserId(),
          version.getApprovedAt(), version.getCreatedAt(), version.getUpdatedAt());
    }
  }

  public record DetailResponse(WorkItemResponse item, ReadinessResponse readiness,
      List<EvidenceResponse> evidence, List<AssessmentResponse> assessments,
      List<SolutionVersionResponse> solutionVersions, List<SnapshotResponse> snapshots) {
  }

  @PostMapping
  public WorkItemResponse create(@PathVariable String projectId,
      @RequestBody CreateWorkItemRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return WorkItemResponse.from(workItemService.create(actor, projectId, request.title(),
        request.summary(), request.priority(), request.sourceNote()));
  }

  @GetMapping
  public List<WorkItemResponse> list(@PathVariable String projectId,
      @RequestParam(value = "status", required = false) String status) {
    AppUser actor = CurrentUser.require(authorizationService);
    return workItemService.list(actor, projectId, status).stream()
        .map(WorkItemResponse::from).toList();
  }

  @GetMapping("/{workItemId}")
  public DetailResponse detail(@PathVariable String projectId, @PathVariable String workItemId) {
    AppUser actor = CurrentUser.require(authorizationService);
    WorkItemService.Detail detail = workItemService.detail(actor, projectId, workItemId);
    return new DetailResponse(
        WorkItemResponse.from(detail.item()),
        ReadinessResponse.from(detail.readiness()),
        detail.evidence().stream().map(EvidenceResponse::from).toList(),
        detail.readiness().dimensions().stream()
            .map(dimension -> new AssessmentResponse(dimension.dimension(), dimension.critical(),
                dimension.state(), dimension.note(), dimension.assumptionNote(),
                dimension.decisionOwner(), dimension.updatedBy(), dimension.updatedAt()))
            .toList(),
        detail.solutionVersions().stream().map(SolutionVersionResponse::from).toList(),
        detail.snapshots().stream().map(SnapshotResponse::from).toList());
  }

  @PutMapping("/{workItemId}")
  public WorkItemResponse update(@PathVariable String projectId, @PathVariable String workItemId,
      @RequestBody UpdateWorkItemRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return WorkItemResponse.from(workItemService.update(actor, projectId, workItemId,
        request.title(), request.summary(), request.priority(), request.ownerUserId()));
  }

  @PostMapping("/{workItemId}/evidence")
  public EvidenceResponse addEvidence(@PathVariable String projectId,
      @PathVariable String workItemId, @RequestBody AddEvidenceRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return EvidenceResponse.from(workItemService.addEvidence(actor, projectId, workItemId,
        request.evidenceType(), request.text(), request.artifactId(), request.locator(),
        request.capturedAt()));
  }

  @PutMapping("/{workItemId}/assessments/{dimension}")
  public AssessmentResponse assess(@PathVariable String projectId, @PathVariable String workItemId,
      @PathVariable String dimension, @RequestBody AssessmentRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    RequirementAssessment assessment = workItemService.assess(actor, projectId, workItemId,
        dimension, request.state(), request.note(), request.assumptionNote(),
        request.decisionOwner());
    return new AssessmentResponse(assessment.getDimension(),
        com.mammoth.domain.workitem.RequirementDimension.isCritical(assessment.getDimension()),
        assessment.getState(), assessment.getNote(), assessment.getAssumptionNote(),
        assessment.getDecisionOwner(), assessment.getUpdatedBy(), assessment.getUpdatedAt());
  }

  @GetMapping("/{workItemId}/readiness")
  public ReadinessResponse readiness(@PathVariable String projectId,
      @PathVariable String workItemId) {
    AppUser actor = CurrentUser.require(authorizationService);
    return ReadinessResponse.from(workItemService.readiness(actor, projectId, workItemId));
  }

  @PostMapping("/{workItemId}/confirm-readiness")
  public ReadinessResponse confirmReadiness(@PathVariable String projectId,
      @PathVariable String workItemId) {
    AppUser actor = CurrentUser.require(authorizationService);
    return ReadinessResponse.from(
        workItemService.confirmReadiness(actor, projectId, workItemId));
  }

  @PostMapping("/{workItemId}/transitions")
  public WorkItemResponse transition(@PathVariable String projectId,
      @PathVariable String workItemId, @RequestBody TransitionRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return WorkItemResponse.from(workItemService.transition(actor, projectId, workItemId,
        request.target(), request.comment()));
  }
}
