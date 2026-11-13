package com.mammoth.domain.web;

import com.mammoth.domain.delivery.DeliveryService;
import com.mammoth.domain.delivery.DeliverySnapshot;
import com.mammoth.domain.delivery.HandoffPackageService;
import com.mammoth.domain.delivery.KnowledgeUpdateProposal;
import com.mammoth.domain.execution.ExecutionChange;
import com.mammoth.domain.execution.ExecutionDeviation;
import com.mammoth.domain.execution.ExecutionRecord;
import com.mammoth.domain.execution.ExecutionService;
import com.mammoth.domain.execution.ExecutionTask;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.security.CurrentUser;
import com.mammoth.domain.service.AuthorizationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Delivery loop API (M3): handoff package, implementation records, deviation classification,
 * acceptance matrix, As-built snapshot and knowledge update proposals.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/work-items/{workItemId}")
public class DeliveryController {

  private final DeliveryService deliveryService;
  private final HandoffPackageService handoffPackageService;
  private final ExecutionService executionService;
  private final AuthorizationService authorizationService;

  public DeliveryController(DeliveryService deliveryService,
      HandoffPackageService handoffPackageService, ExecutionService executionService,
      AuthorizationService authorizationService) {
    this.deliveryService = deliveryService;
    this.handoffPackageService = handoffPackageService;
    this.executionService = executionService;
    this.authorizationService = authorizationService;
  }

  public record RecordExecutionRequest(String repositoryId, String baselineId, String toCommit,
      String summary, String testsNote, List<String> completedTaskIds) {
  }

  public record DeviationRequest(String deviationType, String targetType, String targetId,
      String description) {
  }

  public record CreateSnapshotRequest(String title) {
  }

  public record CreateProposalRequest(String snapshotId, String title) {
  }

  public record HandoffResponse(String markdown, String solutionVersionId, int solutionVersion,
      String baselineId, String generatedAt) {
  }

  public record HandoffPackageResponse(String artifactId, String originalName, String sha256,
      long sizeBytes, String downloadUrl, String generatedAt, String solutionVersionId,
      int solutionVersion, String baselineId) {
  }

  public record ExecutionRecordResponse(String id, String projectId, String workItemId,
      String solutionVersionId, String baselineId, String repositoryId, String fromCommit,
      String toCommit, boolean diverged, String summary, String testsNote, String recordedBy,
      String recordedAt) {

    static ExecutionRecordResponse from(ExecutionRecord record) {
      return new ExecutionRecordResponse(record.getId(), record.getProjectId(),
          record.getWorkItemId(), record.getSolutionVersionId(), record.getBaselineId(),
          record.getRepositoryId(), record.getFromCommit(), record.getToCommit(),
          record.isDiverged(), record.getSummary(), record.getTestsNote(), record.getRecordedBy(),
          record.getRecordedAt());
    }
  }

  public record ChangeResponse(String id, String executionRecordId, String filePath,
      String changeType, String oldPath, int additions, int deletions) {

    static ChangeResponse from(ExecutionChange change) {
      return new ChangeResponse(change.getId(), change.getExecutionRecordId(), change.getFilePath(),
          change.getChangeType(), change.getOldPath(), change.getAdditions(),
          change.getDeletions());
    }
  }

  public record TaskStateResponse(String id, String executionRecordId, String taskId,
      String status, String note) {

    static TaskStateResponse from(ExecutionTask task) {
      return new TaskStateResponse(task.getId(), task.getExecutionRecordId(), task.getTaskId(),
          task.getStatus(), task.getNote());
    }
  }

  public record DeviationResponse(String id, String executionRecordId, String deviationType,
      String targetType, String targetId, String description, String createdBy, String createdAt) {

    static DeviationResponse from(ExecutionDeviation deviation) {
      return new DeviationResponse(deviation.getId(), deviation.getExecutionRecordId(),
          deviation.getDeviationType(), deviation.getTargetType(), deviation.getTargetId(),
          deviation.getDescription(), deviation.getCreatedBy(), deviation.getCreatedAt());
    }
  }

  public record ExecutionDetailResponse(List<ExecutionRecordResponse> records,
      List<ChangeResponse> changes, List<TaskStateResponse> taskStates,
      List<DeviationResponse> deviations) {
  }

  public record MatrixRowResponse(String criterionId, String criterionText, String designItemTitle,
      String coverage, int totalTasks, int doneTasks, int partialTasks) {
  }

  public record MissingTaskResponse(String taskId, String title, String status) {
  }

  public record AnalysisResponse(String solutionVersionId, List<MatrixRowResponse> matrix,
      List<MissingTaskResponse> missingTasks, List<DeviationResponse> deviations,
      List<ExecutionRecordResponse> records, int covered, int partial, int unverified) {
  }

  public record SnapshotResponse(String id, String projectId, String workItemId,
      String solutionVersionId, String baselineId, String title, String bodyMarkdown,
      String status, String artifactId, String createdBy, String createdAt, String approvedBy,
      String approvedAt) {

    static SnapshotResponse from(DeliverySnapshot snapshot) {
      return new SnapshotResponse(snapshot.getId(), snapshot.getProjectId(),
          snapshot.getWorkItemId(), snapshot.getSolutionVersionId(), snapshot.getBaselineId(),
          snapshot.getTitle(), snapshot.getBodyMarkdown(), snapshot.getStatus(),
          snapshot.getArtifactId(), snapshot.getCreatedBy(), snapshot.getCreatedAt(),
          snapshot.getApprovedBy(), snapshot.getApprovedAt());
    }
  }

  public record ProposalResponse(String id, String projectId, String workItemId,
      String deliverySnapshotId, String title, String bodyMarkdown, String status,
      String knowledgeDocumentId, String createdBy, String createdAt, String decidedBy,
      String decidedAt) {

    static ProposalResponse from(KnowledgeUpdateProposal proposal) {
      return new ProposalResponse(proposal.getId(), proposal.getProjectId(),
          proposal.getWorkItemId(), proposal.getDeliverySnapshotId(), proposal.getTitle(),
          proposal.getBodyMarkdown(), proposal.getStatus(), proposal.getKnowledgeDocumentId(),
          proposal.getCreatedBy(), proposal.getCreatedAt(), proposal.getDecidedBy(),
          proposal.getDecidedAt());
    }
  }

  @GetMapping("/handoff")
  public HandoffResponse handoff(@PathVariable String projectId, @PathVariable String workItemId) {
    AppUser actor = CurrentUser.require(authorizationService);
    var generated = deliveryService.handoff(actor, projectId, workItemId);
    return new HandoffResponse(generated.markdown(), generated.solutionVersionId(),
        generated.solutionVersion(), generated.baselineId(), generated.generatedAt());
  }

  /** BG-01: generate a downloadable zip with manifest + sha256 and audit the export. */
  @GetMapping("/handoff/package")
  public HandoffPackageResponse handoffPackage(@PathVariable String projectId,
      @PathVariable String workItemId) {
    AppUser actor = CurrentUser.require(authorizationService);
    HandoffPackageService.PackageView view = handoffPackageService.export(actor, projectId,
        workItemId);
    return new HandoffPackageResponse(view.artifactId(), view.originalName(), view.sha256(),
        view.sizeBytes(), view.downloadUrl(), view.generatedAt(), view.solutionVersionId(),
        view.solutionVersion(), view.baselineId());
  }

  @GetMapping("/executions")
  public ExecutionDetailResponse executions(@PathVariable String projectId,
      @PathVariable String workItemId) {
    AppUser actor = CurrentUser.require(authorizationService);
    ExecutionService.Detail detail = executionService.detail(actor, projectId, workItemId);
    return new ExecutionDetailResponse(
        detail.records().stream().map(ExecutionRecordResponse::from).toList(),
        detail.changes().stream().map(ChangeResponse::from).toList(),
        detail.taskStates().stream().map(TaskStateResponse::from).toList(),
        detail.deviations().stream().map(DeviationResponse::from).toList());
  }

  @PostMapping("/executions")
  public ExecutionRecordResponse recordExecution(@PathVariable String projectId,
      @PathVariable String workItemId, @RequestBody RecordExecutionRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return ExecutionRecordResponse.from(executionService.record(actor, projectId, workItemId,
        request.repositoryId(), request.baselineId(), request.toCommit(), request.summary(),
        request.testsNote(), request.completedTaskIds()));
  }

  @PostMapping("/executions/{executionId}/deviations")
  public DeviationResponse addDeviation(@PathVariable String projectId,
      @PathVariable String workItemId, @PathVariable String executionId,
      @RequestBody DeviationRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return DeviationResponse.from(executionService.addDeviation(actor, projectId, workItemId,
        executionId, request.deviationType(), request.targetType(), request.targetId(),
        request.description()));
  }

  @GetMapping("/acceptance-matrix")
  public AnalysisResponse acceptanceMatrix(@PathVariable String projectId,
      @PathVariable String workItemId) {
    AppUser actor = CurrentUser.require(authorizationService);
    ExecutionService.Analysis analysis = executionService.analysis(actor, projectId, workItemId);
    return new AnalysisResponse(analysis.solutionVersionId(),
        analysis.matrix().stream()
            .map(row -> new MatrixRowResponse(row.criterionId(), row.criterionText(),
                row.designItemTitle(), row.coverage(), row.totalTasks(), row.doneTasks(),
                row.partialTasks()))
            .toList(),
        analysis.missingTasks().stream()
            .map(task -> new MissingTaskResponse(task.taskId(), task.title(), task.status()))
            .toList(),
        analysis.deviations().stream().map(DeviationResponse::from).toList(),
        analysis.records().stream().map(ExecutionRecordResponse::from).toList(),
        analysis.covered(), analysis.partial(), analysis.unverified());
  }

  @GetMapping("/deliveries")
  public List<SnapshotResponse> deliveries(@PathVariable String projectId,
      @PathVariable String workItemId) {
    AppUser actor = CurrentUser.require(authorizationService);
    return deliveryService.listSnapshots(actor, projectId, workItemId).stream()
        .map(SnapshotResponse::from).toList();
  }

  @PostMapping("/deliveries")
  public SnapshotResponse createDelivery(@PathVariable String projectId,
      @PathVariable String workItemId, @RequestBody CreateSnapshotRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return SnapshotResponse.from(
        deliveryService.createSnapshot(actor, projectId, workItemId, request.title()));
  }

  @GetMapping("/deliveries/{snapshotId}")
  public SnapshotResponse delivery(@PathVariable String projectId,
      @PathVariable String workItemId, @PathVariable String snapshotId) {
    AppUser actor = CurrentUser.require(authorizationService);
    return SnapshotResponse.from(
        deliveryService.requireSnapshot(actor, projectId, workItemId, snapshotId));
  }

  @PostMapping("/deliveries/{snapshotId}/approve")
  public SnapshotResponse approveDelivery(@PathVariable String projectId,
      @PathVariable String workItemId, @PathVariable String snapshotId) {
    AppUser actor = CurrentUser.require(authorizationService);
    return SnapshotResponse.from(
        deliveryService.approveSnapshot(actor, projectId, workItemId, snapshotId));
  }

  @GetMapping("/knowledge-proposals")
  public List<ProposalResponse> proposals(@PathVariable String projectId,
      @PathVariable String workItemId) {
    AppUser actor = CurrentUser.require(authorizationService);
    return deliveryService.listProposals(actor, projectId, workItemId).stream()
        .map(ProposalResponse::from).toList();
  }

  @PostMapping("/knowledge-proposals")
  public ProposalResponse createProposal(@PathVariable String projectId,
      @PathVariable String workItemId, @RequestBody CreateProposalRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return ProposalResponse.from(deliveryService.createProposal(actor, projectId, workItemId,
        request.snapshotId(), request.title()));
  }

  @PostMapping("/knowledge-proposals/{proposalId}/accept")
  public ProposalResponse acceptProposal(@PathVariable String projectId,
      @PathVariable String workItemId, @PathVariable String proposalId) {
    AppUser actor = CurrentUser.require(authorizationService);
    return ProposalResponse.from(
        deliveryService.acceptProposal(actor, projectId, workItemId, proposalId));
  }

  @PostMapping("/knowledge-proposals/{proposalId}/reject")
  public ProposalResponse rejectProposal(@PathVariable String projectId,
      @PathVariable String workItemId, @PathVariable String proposalId) {
    AppUser actor = CurrentUser.require(authorizationService);
    return ProposalResponse.from(
        deliveryService.rejectProposal(actor, projectId, workItemId, proposalId));
  }
}
