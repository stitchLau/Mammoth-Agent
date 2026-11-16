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
 * <p>The agent may only create proposals; it cannot approve or apply them. Every accepted propose
 * is recorded in the append-only {@code agent_write_audit} chain, and rejected attempts are audited
 * separately so denial evidence survives the failed transaction.
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

  private static final Set<String> ANY_ROLES = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR,
      AuthorizationService.REVIEWER, AuthorizationService.VIEWER);
  private static final int MAX_PAYLOAD_BYTES = 64 * 1024;

  private final AgentProposalRepository proposalRepository;
  private final AgentWriteAuditRepository auditRepository;
  private final AppUserRepository appUserRepository;
  private final ProjectRepository projectRepository;
  private final AuthorizationService authorizationService;
  private final ObjectMapper objectMapper;

  public AgentProposalService(AgentProposalRepository proposalRepository,
      AgentWriteAuditRepository auditRepository, AppUserRepository appUserRepository,
      ProjectRepository projectRepository, AuthorizationService authorizationService,
      ObjectMapper objectMapper) {
    this.proposalRepository = proposalRepository;
    this.auditRepository = auditRepository;
    this.appUserRepository = appUserRepository;
    this.projectRepository = projectRepository;
    this.authorizationService = authorizationService;
    this.objectMapper = objectMapper;
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
    appendAudit(proposal.getId(), AgentWriteAudit.STAGE_PROPOSE, "agent", agent.getId(), "ok",
        AuditHash.fingerprint(canonicalPayload), null);
    return proposal;
  }

  /** Human listing for the workbench; requires project membership (any role). */
  @Transactional(readOnly = true)
  public List<AgentProposal> list(AppUser human, String projectId) {
    authorizationService.requireProjectRole(human, projectId, ANY_ROLES);
    return proposalRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
  }

  /**
   * Record a denial that happens before/around proposal creation (bad service token, malformed
   * request). Uses a separate transaction so the evidence survives the API error.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordDenied(String actorUserId, String payloadForFingerprint, String reason) {
    String fingerprint = payloadForFingerprint == null
        ? null : AuditHash.fingerprint(payloadForFingerprint);
    appendAudit("unresolved", AgentWriteAudit.STAGE_DENY, "agent", actorUserId,
        "denied:" + (reason == null ? "unspecified" : reason), fingerprint, null);
  }

  /** Risk is derived server-side from the type; callers cannot downgrade it. */
  static String deriveRisk(String proposalType) {
    return HIGH_RISK_TYPES.contains(proposalType)
        ? AgentProposal.RISK_HIGH : AgentProposal.RISK_NORMAL;
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

  private void appendAudit(String proposalId, String stage, String actorKind, String actorUserId,
      String outcome, String requestFingerprint, String domainObjectId) {
    String prevHash = auditRepository.findFirstByProposalIdOrderByAtDescIdDesc(proposalId)
        .map(AgentWriteAudit::getEntryHash)
        .orElse(null);
    String at = Ids.nowIso();
    String entryHash = AuditHash.entryHash(prevHash, proposalId, stage, actorKind, actorUserId,
        outcome, at);
    auditRepository.save(new AgentWriteAudit(Ids.newId(), proposalId, stage, actorKind,
        actorUserId, outcome, requestFingerprint, domainObjectId, prevHash, entryHash, at));
  }
}
