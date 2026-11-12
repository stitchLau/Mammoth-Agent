package com.mammoth.domain.mcp;

import com.mammoth.domain.knowledge.KnowledgeDocument;
import com.mammoth.domain.knowledge.KnowledgeSearchAdapter;
import com.mammoth.domain.knowledge.KnowledgeService;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.web.ApiException;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only knowledge capabilities exposed to DSH agents through MCP (ADR-0001/0005).
 *
 * <p>Every call is authenticated as a configured local user and authorized against project
 * membership; only {@code verified} documents are visible to agents (drafts must never leak).
 */
@Service
public class McpToolService {

  private static final Set<String> READ_ROLES = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR,
      AuthorizationService.REVIEWER, AuthorizationService.VIEWER);

  private final AuthorizationService authorizationService;
  private final KnowledgeSearchAdapter searchAdapter;
  private final KnowledgeService knowledgeService;

  public McpToolService(
      AuthorizationService authorizationService,
      KnowledgeSearchAdapter searchAdapter,
      KnowledgeService knowledgeService) {
    this.authorizationService = authorizationService;
    this.searchAdapter = searchAdapter;
    this.knowledgeService = knowledgeService;
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

  private AppUser authorize(String agentUserId, String projectId) {
    AppUser agent = authorizationService.requireActiveUser(agentUserId);
    authorizationService.requireProjectRole(agent, projectId, READ_ROLES);
    return agent;
  }
}
