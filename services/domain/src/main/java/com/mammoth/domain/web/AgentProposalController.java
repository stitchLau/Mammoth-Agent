package com.mammoth.domain.web;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.mammoth.domain.agentwrite.AgentProposal;
import com.mammoth.domain.agentwrite.AgentProposalService;
import com.mammoth.domain.config.AgentWriteProperties;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.security.CurrentUser;
import com.mammoth.domain.service.AuthorizationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADR-0015 P1 API: agents may only create proposals; listing is a human-session endpoint.
 *
 * <p>The agent endpoint is authenticated by the machine-local service token (P2 replaces this with
 * an isolated OS identity). A blank token or agent identity disables the endpoint (fail-closed).
 */
@RestController
@RequestMapping("/api/agent/proposals")
public class AgentProposalController {

  public static final String AGENT_TOKEN_HEADER = "X-Mammoth-Agent-Token";

  private final AgentProposalService proposalService;
  private final AgentWriteProperties properties;
  private final AuthorizationService authorizationService;
  private final ObjectMapper objectMapper;

  public AgentProposalController(AgentProposalService proposalService,
      AgentWriteProperties properties, AuthorizationService authorizationService,
      ObjectMapper objectMapper) {
    this.proposalService = proposalService;
    this.properties = properties;
    this.authorizationService = authorizationService;
    this.objectMapper = objectMapper;
  }

  public record ProposeRequest(String projectId, String proposalType, JsonNode payload,
      String summary, String idempotencyKey, String agentRunRef) {
  }

  public record ProposalResponse(String id, String projectId, String proposalType, String summary,
      String status, String riskLevel, String proposerKind, String proposerUserId,
      String createdAt, String decidedAt, String appliedAt) {

    static ProposalResponse from(AgentProposal proposal) {
      return new ProposalResponse(proposal.getId(), proposal.getProjectId(),
          proposal.getProposalType(), proposal.getSummary(), proposal.getStatus(),
          proposal.getRiskLevel(), proposal.getProposerKind(), proposal.getProposerUserId(),
          proposal.getCreatedAt(), proposal.getDecidedAt(), proposal.getAppliedAt());
    }
  }

  public record DecisionRequest(String reason) {
  }

  public record ApplyResponse(String proposalId, String status, String domainObjectType,
      String domainObjectId, String domainObjectItemNo, String failureReason) {

    static ApplyResponse from(AgentProposalService.ApplyResult result) {
      return new ApplyResponse(result.proposalId(), result.status(), result.domainObjectType(),
          result.domainObjectId(), result.domainObjectItemNo(), result.failureReason());
    }
  }

  @PostMapping
  public ProposalResponse propose(
      @RequestHeader(value = AGENT_TOKEN_HEADER, required = false) String serviceToken,
      @RequestBody ProposeRequest request) {
    requireServiceToken(serviceToken);
    String payloadJson;
    try {
      payloadJson = objectMapper.writeValueAsString(
          request.payload() == null ? objectMapper.createObjectNode() : request.payload());
    } catch (RuntimeException error) {
      throw ApiException.badRequest("payload must be valid JSON");
    }
    AgentProposal proposal = proposalService.propose(properties.getAgentUserId(),
        request.projectId(), request.proposalType(), payloadJson, request.summary(),
        request.idempotencyKey(), request.agentRunRef());
    return ProposalResponse.from(proposal);
  }

  @GetMapping
  public List<ProposalResponse> list(@RequestParam String projectId) {
    AppUser human = CurrentUser.require(authorizationService);
    return proposalService.list(human, projectId).stream()
        .map(ProposalResponse::from)
        .toList();
  }

  @PostMapping("/{proposalId}/approve")
  public ProposalResponse approve(@PathVariable String proposalId,
      @RequestBody(required = false) DecisionRequest request) {
    AppUser human = CurrentUser.require(authorizationService);
    return ProposalResponse.from(proposalService.approve(human, proposalId,
        request == null ? null : request.reason()));
  }

  @PostMapping("/{proposalId}/reject")
  public ProposalResponse reject(@PathVariable String proposalId,
      @RequestBody(required = false) DecisionRequest request) {
    AppUser human = CurrentUser.require(authorizationService);
    return ProposalResponse.from(proposalService.reject(human, proposalId,
        request == null ? null : request.reason()));
  }

  /** Apply an approved proposal; executed by the approval service identity. */
  @PostMapping("/{proposalId}/apply")
  public ApplyResponse apply(
      @RequestHeader(value = AGENT_TOKEN_HEADER, required = false) String serviceToken,
      @PathVariable String proposalId) {
    requireServiceToken(serviceToken);
    return ApplyResponse.from(proposalService.apply(proposalId, "approval-service"));
  }

  private void requireServiceToken(String provided) {
    if (!properties.configured()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
          "agent write endpoint is not configured");
    }
    byte[] expected = properties.getAgentToken().getBytes(StandardCharsets.UTF_8);
    byte[] actual = provided == null ? new byte[0] : provided.getBytes(StandardCharsets.UTF_8);
    if (!MessageDigest.isEqual(expected, actual)) {
      proposalService.recordDenied(properties.getAgentUserId(), null, "invalid-agent-token");
      throw ApiException.unauthorized("invalid agent service token");
    }
  }
}
