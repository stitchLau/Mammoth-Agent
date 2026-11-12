package com.mammoth.domain.mcp;

import com.mammoth.domain.knowledge.KnowledgeDocument;
import com.mammoth.domain.knowledge.KnowledgeSearchAdapter;
import com.mammoth.domain.knowledge.KnowledgeService;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.solution.SolutionVersion;
import com.mammoth.domain.web.ApiException;
import com.mammoth.domain.workitem.RequirementDimension;
import com.mammoth.domain.workitem.WorkItemService;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only capabilities exposed to DSH agents through MCP (ADR-0001/0005, M2 plan §7).
 *
 * <p>Every call is authenticated as a configured local user and authorized against project
 * membership; only {@code verified} documents are visible to agents (drafts must never leak).
 * Requirement context is readable so an agent can help analyse a requirement, but there is no
 * write path: AI output must re-enter through the human UI/review flow.
 */
@Service
public class McpToolService {

  private static final Set<String> READ_ROLES = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR,
      AuthorizationService.REVIEWER, AuthorizationService.VIEWER);

  private final AuthorizationService authorizationService;
  private final KnowledgeSearchAdapter searchAdapter;
  private final KnowledgeService knowledgeService;
  private final WorkItemService workItemService;

  public McpToolService(
      AuthorizationService authorizationService,
      KnowledgeSearchAdapter searchAdapter,
      KnowledgeService knowledgeService,
      WorkItemService workItemService) {
    this.authorizationService = authorizationService;
    this.searchAdapter = searchAdapter;
    this.knowledgeService = knowledgeService;
    this.workItemService = workItemService;
  }

  public record SearchHitView(String documentId, String title, String chunkId, int ordinal,
      String text, double score, int sourceRefCount) {
  }

  public record SourceRefView(String artifactId, String locator, String note) {
  }

  public record DocumentView(String id, String projectId, String docType, String title, String body,
      String status, String reviewer, String reviewedAt, List<SourceRefView> sourceRefs) {
  }

  @Transactional(readOnly = true)
  public List<SearchHitView> searchProjectKnowledge(String agentUserId, String projectId, String query,
      int topK) {
    AppUser agent = authorize(agentUserId, projectId);
    if (query == null || query.isBlank()) {
      throw ApiException.badRequest("query must not be empty");
    }
    int limit = topK <= 0 ? 5 : Math.min(topK, 20);
    return searchAdapter.search(projectId, query, limit).stream()
        .map(hit -> {
          KnowledgeDocument document = knowledgeService.requireDocument(projectId, hit.documentId());
          return new SearchHitView(hit.documentId(), document.getTitle(), hit.chunkId(),
              hit.ordinal(), hit.text(), hit.score(),
              knowledgeService.sourceRefs(document.getId()).size());
        })
        .toList();
  }

  @Transactional(readOnly = true)
  public DocumentView getKnowledgeDocument(String agentUserId, String projectId, String documentId) {
    authorize(agentUserId, projectId);
    KnowledgeDocument document = knowledgeService.requireDocument(projectId, documentId);
    if (!KnowledgeDocument.VERIFIED.equals(document.getStatus())) {
      throw ApiException.forbidden("only verified knowledge is available to agents");
    }
    List<SourceRefView> refs = knowledgeService.sourceRefs(documentId).stream()
        .map(ref -> new SourceRefView(ref.getArtifactId(), ref.getLocator(), ref.getNote()))
        .toList();
    return new DocumentView(document.getId(), document.getScopeId(), document.getDocType(),
        document.getTitle(), document.getBody(), document.getStatus(), document.getReviewer(),
        document.getReviewedAt(), refs);
  }

  public record RequirementEvidenceView(int batchNo, String type, String text, String artifactId,
      String locator, String capturedAt) {
  }

  public record RequirementAssessmentView(String dimension, boolean critical, String state,
      String note, String assumptionNote, String decisionOwner) {
  }

  public record RequirementContextView(String workItemId, String itemNo, String title,
      String summary, String status, String priority, boolean readinessOk, List<String> blocking,
      String contextSnapshotId, List<RequirementEvidenceView> evidence,
      List<RequirementAssessmentView> assessments, Integer approvedSolutionVersion,
      String approvedSolutionTitle) {
  }

  /** Read-only requirement context so a DSH agent can help analyse gaps without a write path. */
  @Transactional(readOnly = true)
  public RequirementContextView getRequirementContext(String agentUserId, String projectId,
      String workItemId) {
    AppUser agent = authorize(agentUserId, projectId);
    WorkItemService.Detail detail = workItemService.detail(agent, projectId, workItemId);
    List<RequirementEvidenceView> evidence = detail.evidence().stream()
        .map(entry -> new RequirementEvidenceView(entry.getBatchNo(), entry.getEvidenceType(),
            entry.getText(), entry.getArtifactId(), entry.getLocator(), entry.getCapturedAt()))
        .toList();
    List<RequirementAssessmentView> assessments = detail.readiness().dimensions().stream()
        .map(dimension -> new RequirementAssessmentView(dimension.dimension(),
            dimension.critical() || RequirementDimension.isCritical(dimension.dimension()),
            dimension.state(), dimension.note(), dimension.assumptionNote(),
            dimension.decisionOwner()))
        .toList();
    Integer approvedVersion = null;
    String approvedTitle = null;
    for (SolutionVersion version : detail.solutionVersions()) {
      if (version.getId().equals(detail.item().getApprovedSolutionVersionId())) {
        approvedVersion = version.getVersion();
        approvedTitle = version.getTitle();
      }
    }
    return new RequirementContextView(detail.item().getId(), detail.item().getItemNo(),
        detail.item().getTitle(), detail.item().getSummary(), detail.item().getStatus(),
        detail.item().getPriority(), detail.readiness().ready(), detail.readiness().blocking(),
        detail.item().getContextSnapshotId(), evidence, assessments, approvedVersion,
        approvedTitle);
  }

  private AppUser authorize(String agentUserId, String projectId) {
    AppUser agent = authorizationService.requireActiveUser(agentUserId);
    authorizationService.requireProjectRole(agent, projectId, READ_ROLES);
    return agent;
  }
}
