package com.mammoth.domain.workitem;

import com.mammoth.domain.artifact.ArtifactService;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.service.AuditService;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.solution.SolutionVersion;
import com.mammoth.domain.solution.SolutionVersionRepository;
import com.mammoth.domain.support.Ids;
import com.mammoth.domain.support.JsonLite;
import com.mammoth.domain.web.ApiException;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Requirement work item lifecycle (docs/07, M2 plan §4): evidence batches, readiness assessments,
 * the human readiness gate, and guarded status transitions. Every mutation is audited.
 */
@Service
public class WorkItemService {

  private static final Set<String> ANY_ROLES = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR,
      AuthorizationService.REVIEWER, AuthorizationService.VIEWER);
  private static final Set<String> WRITE_ROLES = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR);
  private static final Set<String> DECIDE_ROLES = Set.of(
      AuthorizationService.OWNER, AuthorizationService.REVIEWER);

  private final WorkItemRepository workItemRepository;
  private final RequirementEvidenceRepository evidenceRepository;
  private final RequirementAssessmentRepository assessmentRepository;
  private final SolutionVersionRepository solutionVersionRepository;
  private final ArtifactService artifactService;
  private final AuthorizationService authorizationService;
  private final AuditService auditService;
  private final ReadinessService readinessService;
  private final ContextSnapshotService snapshotService;

  public WorkItemService(
      WorkItemRepository workItemRepository,
      RequirementEvidenceRepository evidenceRepository,
      RequirementAssessmentRepository assessmentRepository,
      SolutionVersionRepository solutionVersionRepository,
      ArtifactService artifactService,
      AuthorizationService authorizationService,
      AuditService auditService,
      ReadinessService readinessService,
      ContextSnapshotService snapshotService) {
    this.workItemRepository = workItemRepository;
    this.evidenceRepository = evidenceRepository;
    this.assessmentRepository = assessmentRepository;
    this.solutionVersionRepository = solutionVersionRepository;
    this.artifactService = artifactService;
    this.authorizationService = authorizationService;
    this.auditService = auditService;
    this.readinessService = readinessService;
    this.snapshotService = snapshotService;
  }

  /** Aggregate view used by the requirement detail page and the MCP context tool. */
  public record Detail(WorkItem item, ReadinessService.ReadinessSummary readiness,
      List<RequirementEvidence> evidence, List<RequirementAssessment> assessments,
      List<SolutionVersion> solutionVersions, List<RequirementSnapshot> snapshots) {
  }

  @Transactional
  public WorkItem create(AppUser actor, String projectId, String title, String summary,
      String priority, String sourceNote) {
    authorizationService.requireProjectRole(actor, projectId, WRITE_ROLES);
    String normalizedTitle = requireText(title, "title");
    long existing = workItemRepository.countByProjectId(projectId);
    String itemNo = String.format("REQ-%04d", existing + 1);
    WorkItem item = new WorkItem(Ids.newId(), projectId, itemNo, normalizedTitle,
        blankToNull(summary), priority, blankToNull(sourceNote), null, actor.getId(), Ids.nowIso());
    workItemRepository.save(item);
    auditService.record(actor.getId(), "work_item.create", "work_item", item.getId(),
        JsonLite.write(JsonLite.object("itemNo", itemNo, "priority", item.getPriority())));
    return item;
  }

  @Transactional(readOnly = true)
  public List<WorkItem> list(AppUser actor, String projectId, String status) {
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLES);
    if (status == null || status.isBlank()) {
      return workItemRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
    }
    if (!WorkItemStatus.isKnown(status)) {
      throw ApiException.badRequest("unknown requirement status: " + status);
    }
    return workItemRepository.findByProjectIdAndStatusOrderByCreatedAtAsc(projectId, status);
  }

  @Transactional(readOnly = true)
  public WorkItem requireItem(String projectId, String workItemId) {
    return workItemRepository.findByIdAndProjectId(workItemId, projectId)
        .orElseThrow(() -> ApiException.notFound("requirement not found: " + workItemId));
  }

  @Transactional(readOnly = true)
  public Detail detail(AppUser actor, String projectId, String workItemId) {
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLES);
    WorkItem item = requireItem(projectId, workItemId);
    List<RequirementAssessment> assessments =
        assessmentRepository.findByWorkItemIdOrderByDimensionAsc(workItemId);
    return new Detail(item, readinessService.evaluate(item, assessments),
        evidenceRepository.findByWorkItemIdOrderByBatchNoAsc(workItemId), assessments,
        solutionVersionRepository.findByWorkItemIdOrderByVersionAsc(workItemId),
        snapshotService.list(workItemId));
  }

  @Transactional
  public WorkItem update(AppUser actor, String projectId, String workItemId, String title,
      String summary, String priority, String ownerUserId) {
    authorizationService.requireProjectRole(actor, projectId, WRITE_ROLES);
    WorkItem item = requireItem(projectId, workItemId);
    String previousTitle = item.getTitle();
    item.updateDetails(requireText(title, "title"), blankToNull(summary), priority,
        blankToNull(ownerUserId), Ids.nowIso());
    workItemRepository.save(item);
    auditService.record(actor.getId(), "work_item.update", "work_item", workItemId,
        JsonLite.write(JsonLite.object("fromTitle", previousTitle, "toTitle", item.getTitle())));
    return item;
  }

  @Transactional
  public RequirementEvidence addEvidence(AppUser actor, String projectId, String workItemId,
      String evidenceType, String text, String artifactId, String locator, String capturedAt) {
    authorizationService.requireProjectRole(actor, projectId, WRITE_ROLES);
    WorkItem item = requireItem(projectId, workItemId);
    requireEditable(item);
    String evidenceText = requireText(text, "evidence text");
    String normalizedArtifactId = blankToNull(artifactId);
    if (normalizedArtifactId != null) {
      // Cross-project artifact references must fail like a missing artifact (no existence leak).
      artifactService.require(projectId, normalizedArtifactId);
    }
    int batchNo = (int) evidenceRepository.countByWorkItemId(workItemId) + 1;
    String now = Ids.nowIso();
    RequirementEvidence evidence = new RequirementEvidence(Ids.newId(), workItemId, batchNo,
        evidenceType, evidenceText, normalizedArtifactId, blankToNull(locator), capturedAt,
        actor.getId(), now);
    evidenceRepository.save(evidence);

    if (WorkItemStatus.DRAFT.equals(item.getStatus())) {
      WorkItemStatus.requireTransition(item.getStatus(), WorkItemStatus.COLLECTING, item.getPriorStatus());
      item.applyTransition(WorkItemStatus.COLLECTING, now);
      workItemRepository.save(item);
    }
    auditService.record(actor.getId(), "work_item.evidence_add", "work_item", workItemId,
        JsonLite.write(JsonLite.object("batchNo", batchNo, "type", evidence.getEvidenceType(),
            "artifactId", normalizedArtifactId)));
    return evidence;
  }

  @Transactional
  public RequirementAssessment assess(AppUser actor, String projectId, String workItemId,
      String dimension, String state, String note, String assumptionNote, String decisionOwner) {
    authorizationService.requireProjectRole(actor, projectId, WRITE_ROLES);
    WorkItem item = requireItem(projectId, workItemId);
    requireEditable(item);
    RequirementDimension.requireKnown(dimension);
    RequirementDimension.requireValidState(state);
    RequirementAssessment assessment = assessmentRepository
        .findByWorkItemIdAndDimension(workItemId, dimension)
        .orElseGet(() -> new RequirementAssessment(Ids.newId(), workItemId, dimension));
    assessment.update(state, note, assumptionNote, decisionOwner, actor.getId(), Ids.nowIso());
    assessmentRepository.save(assessment);
    auditService.record(actor.getId(), "work_item.assess", "work_item", workItemId,
        JsonLite.write(JsonLite.object("dimension", dimension, "state", state)));
    return assessment;
  }

  @Transactional(readOnly = true)
  public ReadinessService.ReadinessSummary readiness(AppUser actor, String projectId,
      String workItemId) {
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLES);
    WorkItem item = requireItem(projectId, workItemId);
    return readinessService.evaluate(item,
        assessmentRepository.findByWorkItemIdOrderByDimensionAsc(workItemId));
  }

  /**
   * Human readiness gate: the requirement owner (or a reviewer) confirms that open dimensions are
   * acceptable. Blocking dimensions reject the gate with the full reason list (409).
   */
  @Transactional
  public ReadinessService.ReadinessSummary confirmReadiness(AppUser actor, String projectId,
      String workItemId) {
    authorizationService.requireProjectRole(actor, projectId, DECIDE_ROLES);
    WorkItem item = requireItem(projectId, workItemId);
    requireEditable(item);
    if (!WorkItemStatus.COLLECTING.equals(item.getStatus())) {
      throw ApiException.conflict(
          "requirement must be in collecting to confirm readiness (current: " + item.getStatus() + ")");
    }
    List<RequirementAssessment> assessments =
        assessmentRepository.findByWorkItemIdOrderByDimensionAsc(workItemId);
    ReadinessService.ReadinessSummary summary = readinessService.evaluate(item, assessments);
    if (!summary.ready()) {
      throw ApiException.conflict(
          "requirement is not ready: " + String.join("; ", summary.blocking()));
    }
    String now = Ids.nowIso();
    RequirementSnapshot snapshot = snapshotService.capture(actor, item, "readiness");
    WorkItemStatus.requireTransition(item.getStatus(), WorkItemStatus.READY_FOR_ANALYSIS,
        item.getPriorStatus());
    item.applyTransition(WorkItemStatus.READY_FOR_ANALYSIS, now);
    item.confirmReadiness(actor.getId(), snapshot.getId(), now);
    workItemRepository.save(item);
    auditService.record(actor.getId(), "work_item.readiness_confirm", "work_item", workItemId,
        JsonLite.write(JsonLite.object("snapshotId", snapshot.getId(),
            "snapshotVersion", snapshot.getVersion())));
    return readinessService.evaluate(item, assessments);
  }

  /**
   * Guarded transitions that are not driven by a dedicated command: pause/resume, withdrawal,
   * rejection and starting analysis. Readiness confirmation, solution review and approval have
   * their own endpoints so the gate rules cannot be bypassed.
   */
  @Transactional
  public WorkItem transition(AppUser actor, String projectId, String workItemId, String target,
      String comment) {
    WorkItem item = requireItem(projectId, workItemId);
    if (WorkItemStatus.READY_FOR_ANALYSIS.equals(target)
        || WorkItemStatus.SOLUTION_REVIEW.equals(target)
        || WorkItemStatus.APPROVED.equals(target)) {
      throw ApiException.badRequest(
          "status " + target + " is reached through its dedicated command, not a generic transition");
    }
    String normalizedComment = blankToNull(comment);
    if (WorkItemStatus.WITHDRAWN.equals(target)) {
      boolean creator = actor.getId().equals(item.getCreatedBy());
      authorizationService.requireProjectRole(actor, projectId,
          creator ? ANY_ROLES : Set.of(AuthorizationService.OWNER));
    } else if (WorkItemStatus.REJECTED.equals(target)) {
      authorizationService.requireProjectRole(actor, projectId, DECIDE_ROLES);
      if (normalizedComment == null) {
        throw ApiException.badRequest("rejecting a requirement requires a comment");
      }
    } else {
      authorizationService.requireProjectRole(actor, projectId, WRITE_ROLES);
    }
    String from = item.getStatus();
    WorkItemStatus.requireTransition(from, target, item.getPriorStatus());
    item.applyTransition(target, Ids.nowIso());
    workItemRepository.save(item);
    auditService.record(actor.getId(), "work_item.transition", "work_item", workItemId,
        JsonLite.write(JsonLite.object("from", from, "to", target, "comment", normalizedComment)));
    return item;
  }

  /** Called by the solution service when a solution version changes the requirement status. */
  @Transactional
  public WorkItem transitionFromCommand(AppUser actor, WorkItem item, String target, String action) {
    String from = item.getStatus();
    WorkItemStatus.requireTransition(from, target, item.getPriorStatus());
    item.applyTransition(target, Ids.nowIso());
    workItemRepository.save(item);
    auditService.record(actor.getId(), "work_item.transition", "work_item", item.getId(),
        JsonLite.write(JsonLite.object("from", from, "to", target, "reason", action)));
    return item;
  }

  @Transactional
  public RequirementSnapshot captureSnapshot(AppUser actor, WorkItem item, String purpose) {
    return snapshotService.capture(actor, item, purpose);
  }

  private void requireEditable(WorkItem item) {
    if (WorkItemStatus.freezesContent(item.getStatus())) {
      throw ApiException.conflict(
          "requirement is " + item.getStatus() + " and can no longer be edited");
    }
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
