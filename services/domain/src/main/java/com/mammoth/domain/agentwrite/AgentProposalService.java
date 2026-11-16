package com.mammoth.domain.agentwrite;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.AppUserRepository;
import com.mammoth.domain.persistence.Project;
import com.mammoth.domain.persistence.ProjectRepository;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.support.Ids;
import com.mammoth.domain.web.ApiException;
import com.mammoth.domain.workitem.WorkItem;
import com.mammoth.domain.workitem.WorkItemService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR-0015 P1: the agent-facing half of controlled writes.
 *
 * <p>The agent may only create proposals; it cannot approve or apply them. Humans decide through
 * {@link #approve}/{@link #reject} (four-eyes: proposer != approver, high-risk needs a second
 * different human). The approval service applies an approved proposal through
 * {@link #apply}, which writes through the existing Domain service as the approving human.
 */
@Service
public class AgentProposalService {

  public static final String TYPE_WORK_ITEM_CREATE = "work_item.create";

  /** Proposal types the agent may submit in P1. */
  public static final Set<String> SUPPORTED_TYPES = Set.of(TYPE_WORK_ITEM_CREATE);

  /**
   * ADR-0015 decision 4 high-risk categories (v0): finalizing a solution, closing a delivery,
   * bulk/delete operations, major requirement-state transitions and customer-data export/outbound
   * writes. These require a second human reviewer; types not listed here are normal risk.
   */
  public static final Set<String> HIGH_RISK_TYPES = Set.of(
      "solution.finalize",
      "delivery.close",
      "work_item.delete",
      "work_item.bulk_delete",
      "work_item.major_transition",
      "data.export");

  /** Approvers must be able to execute the write (owner/editor can create work items). */
  private static final Set<String> APPROVE_ROLES = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR);
  /** Reviewers may reject, but approval for execution needs write authority. */
  private static final Set<String> REJECT_ROLES = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR,
      AuthorizationService.REVIEWER);
  private static final Set<String> ANY_ROLES = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR,
      AuthorizationService.REVIEWER, AuthorizationService.VIEWER);
  private static final int MAX_PAYLOAD_BYTES = 64 * 1024;
  private static final String SERVICE_ACTOR = "approval-service";

  private final AgentProposalRepository proposalRepository;
  private final AgentProposalApprovalRepository approvalRepository;
  private final AgentWriteAuditService auditService;
  private final AgentProposalStateService stateService;
  private final AppUserRepository appUserRepository;
  private final ProjectRepository projectRepository;
  private final AuthorizationService authorizationService;
  private final WorkItemService workItemService;
  private final ObjectMapper objectMapper;

  public AgentProposalService(AgentProposalRepository proposalRepository,
      AgentProposalApprovalRepository approvalRepository, AgentWriteAuditService auditService,
      AgentProposalStateService stateService, AppUserRepository appUserRepository,
      ProjectRepository projectRepository, AuthorizationService authorizationService,
      WorkItemService workItemService, ObjectMapper objectMapper) {
    this.proposalRepository = proposalRepository;
    this.approvalRepository = approvalRepository;
    this.auditService = auditService;
    this.stateService = stateService;
    this.appUserRepository = appUserRepository;
    this.projectRepository = projectRepository;
    this.authorizationService = authorizationService;
    this.workItemService = workItemService;
    this.objectMapper = objectMapper;
  }

  /** Result of applying an approved proposal; also used for idempotent repeats. */
  public record ApplyResult(String proposalId, String status, String domainObjectType,
      String domainObjectId, String domainObjectItemNo, String failureReason) {

    static ApplyResult applied(String proposalId, String status, String domainObjectId,
        String itemNo) {
      return new ApplyResult(proposalId, status, "work_item", domainObjectId, itemNo, null);
    }

    static ApplyResult failed(String proposalId, String failureReason) {
      return new ApplyResult(proposalId, AgentProposal.STATUS_FAILED, null, null, null,
          failureReason);
    }
  }

  /**
   * Create (or return the existing, for the same idempotency key) an agent proposal. The caller is
   * already authenticated as the configured agent service identity.
   */
  @Transactional
  public AgentProposal propose(String agentUserId, String projectId, String proposalType,
      String payloadJson, String summary, String idempotencyKey, String agentRunRef) {
    AppUser agent = appUserRepository.findById(agentUserId)
        .filter(AppUser::isActive)
        .orElseThrow(() -> ApiException.unauthorized("unknown or inactive agent identity"));
    Project project = projectRepository.findById(projectId)
        .orElseThrow(() -> ApiException.notFound("project not found: " + projectId));
    authorizationService.requireProjectRole(agent, project.getId(), ANY_ROLES);

    String type = requireSupportedType(proposalType);
    String canonicalPayload = validatePayload(type, payloadJson);
    String key = requireIdempotencyKey(idempotencyKey);

    Optional<AgentProposal> existing = proposalRepository.findByIdempotencyKey(key);
    if (existing.isPresent()) {
      AgentProposal prior = existing.get();
      if (!prior.getProjectId().equals(project.getId())
          || !prior.getProposalType().equals(type)) {
        throw ApiException.conflict(
            "idempotency key is already used by a different proposal: " + prior.getId());
      }
      return prior;
    }

    AgentProposal proposal = new AgentProposal(Ids.newId(), project.getId(), type, canonicalPayload,
        blankToNull(summary), AgentProposal.PROPOSER_AGENT, agent.getId(), blankToNull(agentRunRef),
        key, deriveRisk(type), Ids.nowIso());
    proposalRepository.save(proposal);
    auditService.append(proposal.getId(), AgentWriteAudit.STAGE_PROPOSE, "agent", agent.getId(),
        "ok", AuditHash.fingerprint(canonicalPayload), null);
    return proposal;
  }

  /** Human listing for the workbench; requires project membership (any role). */
  @Transactional(readOnly = true)
  public List<AgentProposal> list(AppUser human, String projectId) {
    authorizationService.requireProjectRole(human, projectId, ANY_ROLES);
    return proposalRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
  }

  /** Approval-service work queue: approved proposals waiting for apply. */
  @Transactional(readOnly = true)
  public List<AgentProposal> listApproved(String projectId) {
    return proposalRepository.findByProjectIdAndStatusOrderByCreatedAtAsc(projectId,
        AgentProposal.STATUS_APPROVED);
  }

  /**
   * Host/agent read model: proposals proposed by the configured agent identity (optionally
   * restricted to one DSH session). Read-only; no human session needed.
   */
  @Transactional(readOnly = true)
  public List<AgentProposal> listMine(String agentUserId, String projectId, String agentRunRef) {
    List<AgentProposal> proposals =
        proposalRepository.findByProjectIdAndProposerUserIdOrderByCreatedAtAsc(projectId,
            agentUserId);
    if (agentRunRef == null || agentRunRef.isBlank()) {
      return proposals;
    }
    return proposals.stream()
        .filter(proposal -> agentRunRef.equals(proposal.getAgentRunRef()))
        .toList();
  }

  /** Record a human approval; a high-risk proposal needs a second, different human. */
  @Transactional
  public AgentProposal approve(AppUser human, String proposalId, String reason) {
    AgentProposal proposal = requireProposal(proposalId);
    authorizeDecider(human, proposal, APPROVE_ROLES, "approve");
    if (!proposal.isOpen()) {
      deny(human, proposal, "approve:not-open");
      throw ApiException.conflict("cannot approve a proposal in status " + proposal.getStatus());
    }
    boolean second = AgentProposal.STATUS_AWAITING_SECOND.equals(proposal.getStatus());
    if (second) {
      AgentProposalApproval primary = requireApproval(proposal, AgentProposalApproval.ROLE_PRIMARY);
      if (primary.getApproverUserId().equals(human.getId())) {
        deny(human, proposal, "approve:second-approver-equal");
        throw ApiException.forbidden(
            "the second reviewer must differ from the primary approver");
      }
    }
    if (second) {
      proposal.approveSecond(human.getId(), Ids.nowIso());
    } else {
      proposal.approvePrimary(human.getId(), Ids.nowIso());
    }
    approvalRepository.save(new AgentProposalApproval(Ids.newId(), proposal.getId(),
        human.getId(), AgentProposalApproval.DECISION_APPROVE, blankToNull(reason),
        second ? AgentProposalApproval.ROLE_SECOND : AgentProposalApproval.ROLE_PRIMARY, null,
        Ids.nowIso()));
    proposalRepository.save(proposal);
    auditService.append(proposal.getId(), AgentWriteAudit.STAGE_APPROVE, "human", human.getId(),
        "ok", AuditHash.fingerprint(proposal.getPayloadJson()), null);
    return proposal;
  }

  /** Record a human rejection (owner/editor/reviewer); no Domain write happens. */
  @Transactional
  public AgentProposal reject(AppUser human, String proposalId, String reason) {
    AgentProposal proposal = requireProposal(proposalId);
    authorizeDecider(human, proposal, REJECT_ROLES, "reject");
    if (!proposal.isOpen()) {
      deny(human, proposal, "reject:not-open");
      throw ApiException.conflict("cannot reject a proposal in status " + proposal.getStatus());
    }
    boolean second = AgentProposal.STATUS_AWAITING_SECOND.equals(proposal.getStatus());
    if (second) {
      AgentProposalApproval primary = requireApproval(proposal, AgentProposalApproval.ROLE_PRIMARY);
      if (primary.getApproverUserId().equals(human.getId())) {
        deny(human, proposal, "reject:second-approver-equal");
        throw ApiException.forbidden(
            "the second reviewer must differ from the primary approver");
      }
    }
    proposal.reject(reason, Ids.nowIso());
    approvalRepository.save(new AgentProposalApproval(Ids.newId(), proposal.getId(),
        human.getId(), AgentProposalApproval.DECISION_REJECT, blankToNull(reason),
        second ? AgentProposalApproval.ROLE_SECOND : AgentProposalApproval.ROLE_PRIMARY, null,
        Ids.nowIso()));
    proposalRepository.save(proposal);
    auditService.append(proposal.getId(), AgentWriteAudit.STAGE_REJECT, "human", human.getId(),
        "ok", AuditHash.fingerprint(proposal.getPayloadJson()), null);
    return proposal;
  }

  /**
   * Apply an approved proposal through the existing Domain write service. Executed by the approval
   * service identity; the business object is created as the approving human so Domain audits keep
   * a human author. Idempotent: a second apply returns the already-created Domain object.
   */
  public ApplyResult apply(String proposalId, String serviceActor) {
    AgentProposal proposal = requireProposal(proposalId);
    if (AgentProposal.STATUS_APPLIED.equals(proposal.getStatus())) {
      return ApplyResult.applied(proposal.getId(), proposal.getStatus(),
          proposal.getDomainObjectId(), null);
    }
    if (!AgentProposal.STATUS_APPROVED.equals(proposal.getStatus())) {
      auditService.appendIndependent(proposal.getId(), AgentWriteAudit.STAGE_APPLY, "service",
          serviceActor == null ? SERVICE_ACTOR : serviceActor, "denied:not-approved", null, null);
      throw ApiException.conflict("proposal is not approved: " + proposal.getStatus());
    }
    if (AgentProposal.STATUS_APPLYING.equals(proposal.getStatus())) {
      throw ApiException.conflict("proposal is already being applied");
    }
    AgentProposalApproval primary = requireApproval(proposal, AgentProposalApproval.ROLE_PRIMARY);
    if (proposal.isHighRisk()) {
      AgentProposalApproval second = requireApproval(proposal, AgentProposalApproval.ROLE_SECOND);
      if (second.getApproverUserId().equals(primary.getApproverUserId())) {
        throw ApiException.forbidden("high-risk proposal requires two distinct human approvers");
      }
    }
    AppUser approver = appUserRepository.findById(primary.getApproverUserId())
        .filter(AppUser::isActive)
        .orElseThrow(() -> ApiException.conflict("proposal approver is no longer active"));
    String sourceNote = optionalPayloadText(proposal, "sourceNote");
    String domainSourceNote = sourceNote == null
        ? "mammoth-proposal:" + proposal.getId()
        : sourceNote + " (mammoth-proposal:" + proposal.getId() + ")";
    String actor = serviceActor == null ? SERVICE_ACTOR : serviceActor;

    stateService.markApplying(proposal.getId());
    try {
      String title = requiredPayloadText(proposal, "title");
      String summary = optionalPayloadText(proposal, "summary");
      String priority = optionalPayloadText(proposal, "priority");
      WorkItem item = workItemService.create(approver, proposal.getProjectId(), title, summary,
          priority, domainSourceNote);
      stateService.completeApplied(proposal.getId(), actor, item.getId(),
          AuditHash.fingerprint(proposal.getPayloadJson()));
      return ApplyResult.applied(proposal.getId(), AgentProposal.STATUS_APPLIED, item.getId(),
          item.getItemNo());
    } catch (RuntimeException error) {
      String message = error.getMessage() == null ? "domain write failed" : error.getMessage();
      stateService.completeFailed(proposal.getId(), actor, message);
      return ApplyResult.failed(proposal.getId(), message);
    }
  }

  /**
   * Record a denial that happens before/around proposal creation (bad service token, malformed
   * request). Uses a separate transaction so the evidence survives the API error.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordDenied(String actorUserId, String payloadForFingerprint, String reason) {
    String fingerprint = payloadForFingerprint == null
        ? null : AuditHash.fingerprint(payloadForFingerprint);
    auditService.append("unresolved", AgentWriteAudit.STAGE_DENY, "agent", actorUserId,
        "denied:" + (reason == null ? "unspecified" : reason), fingerprint, null);
  }

  /** Risk is derived server-side from the type; callers cannot downgrade it. */
  static String deriveRisk(String proposalType) {
    return HIGH_RISK_TYPES.contains(proposalType)
        ? AgentProposal.RISK_HIGH : AgentProposal.RISK_NORMAL;
  }

  private AgentProposal requireProposal(String proposalId) {
    return proposalRepository.findById(proposalId)
        .orElseThrow(() -> ApiException.notFound("proposal not found: " + proposalId));
  }

  private AgentProposalApproval requireApproval(AgentProposal proposal, String reviewerRole) {
    return approvalRepository.findByProposalIdAndReviewerRole(proposal.getId(), reviewerRole)
        .filter(record -> AgentProposalApproval.DECISION_APPROVE.equals(record.getDecision()))
        .orElseThrow(() -> ApiException.conflict(
            "proposal is missing the " + reviewerRole + " human approval"));
  }

  private void authorizeDecider(AppUser human, AgentProposal proposal, Set<String> roles,
      String action) {
    try {
      authorizationService.requireProjectRole(human, proposal.getProjectId(), roles);
    } catch (ApiException error) {
      deny(human, proposal, action + ":forbidden");
      throw error;
    }
  }

  private void deny(AppUser human, AgentProposal proposal, String reason) {
    auditService.appendIndependent(proposal.getId(), AgentWriteAudit.STAGE_DENY, "human",
        human.getId(), "denied:" + reason, null, null);
  }

  private String requireSupportedType(String proposalType) {
    String normalized = proposalType == null ? "" : proposalType.trim();
    if (!SUPPORTED_TYPES.contains(normalized)) {
      throw ApiException.badRequest("unsupported proposal type: " + proposalType);
    }
    return normalized;
  }

  private static String requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw ApiException.badRequest("idempotencyKey is required");
    }
    String normalized = idempotencyKey.trim();
    if (normalized.length() > 200) {
      throw ApiException.badRequest("idempotencyKey is too long");
    }
    return normalized;
  }

  /** Validate a payload against the type's schema and return a canonical, filtered JSON string. */
  private String validatePayload(String proposalType, String payloadJson) {
    if (payloadJson == null || payloadJson.isBlank()) {
      throw ApiException.badRequest("payload is required");
    }
    if (payloadJson.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
      throw ApiException.badRequest("payload is too large");
    }
    JsonNode payload;
    try {
      payload = objectMapper.readTree(payloadJson);
    } catch (RuntimeException error) {
      throw ApiException.badRequest("payload must be valid JSON");
    }
    if (payload == null || !payload.isObject()) {
      throw ApiException.badRequest("payload must be a JSON object");
    }
    ObjectNode canonical = objectMapper.createObjectNode();
    if (TYPE_WORK_ITEM_CREATE.equals(proposalType)) {
      canonical.put("title", requiredText(payload, "title"));
      putOptionalText(canonical, payload, "summary");
      putOptionalText(canonical, payload, "sourceNote");
      String priority = optionalText(payload, "priority");
      if (priority != null) {
        canonical.put("priority", WorkItem.normalizePriority(priority));
      }
    }
    return objectMapper.writeValueAsString(canonical);
  }

  private String requiredPayloadText(AgentProposal proposal, String field) {
    JsonNode payload = parseStoredPayload(proposal);
    String value = optionalText(payload, field);
    if (value == null || value.isEmpty()) {
      throw ApiException.badRequest("stored payload is missing " + field);
    }
    return value;
  }

  private String optionalPayloadText(AgentProposal proposal, String field) {
    return optionalText(parseStoredPayload(proposal), field);
  }

  private JsonNode parseStoredPayload(AgentProposal proposal) {
    try {
      JsonNode payload = objectMapper.readTree(proposal.getPayloadJson());
      if (payload == null || !payload.isObject()) {
        throw ApiException.badRequest("stored payload is not a JSON object");
      }
      return payload;
    } catch (ApiException error) {
      throw error;
    } catch (RuntimeException error) {
      throw ApiException.badRequest("stored payload is not valid JSON");
    }
  }

  private static String requiredText(JsonNode payload, String field) {
    String value = optionalText(payload, field);
    if (value == null || value.isEmpty()) {
      throw ApiException.badRequest(field + " is required");
    }
    return value;
  }

  private static void putOptionalText(ObjectNode target, JsonNode payload, String field) {
    String value = optionalText(payload, field);
    if (value != null) {
      target.put(field, value);
    }
  }

  private static String optionalText(JsonNode payload, String field) {
    JsonNode node = payload.get(field);
    if (node == null || node.isNull()) {
      return null;
    }
    if (!node.isTextual()) {
      throw ApiException.badRequest(field + " must be a string");
    }
    String value = node.asText().trim();
    return value.isEmpty() ? null : value;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
