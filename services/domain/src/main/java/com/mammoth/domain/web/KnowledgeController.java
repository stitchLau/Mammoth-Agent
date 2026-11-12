package com.mammoth.domain.web;

import com.mammoth.domain.knowledge.KnowledgeDocument;
import com.mammoth.domain.knowledge.KnowledgeSearchAdapter;
import com.mammoth.domain.knowledge.KnowledgeService;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.service.AuthorizationService;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Knowledge document lifecycle and project-scoped search (ADR-0005). */
@RestController
@RequestMapping("/api/projects/{projectId}/knowledge")
public class KnowledgeController {

  private static final String USER_HEADER = CatalogController.USER_HEADER;
  private static final Set<String> ANY_ROLE = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR,
      AuthorizationService.REVIEWER, AuthorizationService.VIEWER);
  private static final Set<String> EDIT_ROLES = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR);

  private final KnowledgeService knowledgeService;
  private final AuthorizationService authorizationService;
  private final KnowledgeSearchAdapter searchAdapter;

  public KnowledgeController(
      KnowledgeService knowledgeService,
      AuthorizationService authorizationService,
      KnowledgeSearchAdapter searchAdapter) {
    this.knowledgeService = knowledgeService;
    this.authorizationService = authorizationService;
    this.searchAdapter = searchAdapter;
  }

  public record SourceRefPayload(String artifactId, String locator, String note) {
  }

  public record CreateKnowledgeRequest(String docType, String title, String body,
      List<SourceRefPayload> sourceRefs) {
  }

  public record SourceRefResponse(String artifactId, String locator, String note) {
  }

  public record KnowledgeResponse(String id, String projectId, String docType, String title,
      String body, String status, int version, String createdBy, String reviewer,
      String reviewedAt, String createdAt, String updatedAt,
      List<SourceRefResponse> sourceRefs) {
  }

  public record SearchHitResponse(String documentId, String title, String chunkId, int ordinal,
      String text, double score, int sourceRefCount) {
  }

  @PostMapping
  public KnowledgeResponse create(
      @RequestHeader(value = USER_HEADER, required = false) String userId,
      @PathVariable String projectId,
      @RequestBody CreateKnowledgeRequest request) {
    AppUser actor = authorizationService.requireActiveUser(userId);
    authorizationService.requireProjectRole(actor, projectId, EDIT_ROLES);
    List<KnowledgeService.SourceRefInput> refs = request.sourceRefs() == null
        ? List.of()
        : request.sourceRefs().stream()
            .map(ref -> new KnowledgeService.SourceRefInput(ref.artifactId(), ref.locator(), ref.note()))
            .toList();
    KnowledgeDocument document = knowledgeService.create(
        actor, projectId, request.docType(), request.title(), request.body(), refs);
    return toResponse(document);
  }

  @GetMapping
  public List<KnowledgeResponse> list(
      @RequestHeader(value = USER_HEADER, required = false) String userId,
      @PathVariable String projectId,
      @RequestParam(value = "status", required = false) String status) {
    AppUser actor = authorizationService.requireActiveUser(userId);
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLE);
    return knowledgeService.list(projectId, status).stream().map(this::toResponse).toList();
  }

  @GetMapping("/search")
  public List<SearchHitResponse> search(
      @RequestHeader(value = USER_HEADER, required = false) String userId,
      @PathVariable String projectId,
      @RequestParam("q") String query,
      @RequestParam(value = "topK", defaultValue = "5") int topK) {
    AppUser actor = authorizationService.requireActiveUser(userId);
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLE);
    return searchAdapter.search(projectId, query, topK).stream()
        .map(hit -> {
          try {
            KnowledgeDocument document = knowledgeService.requireDocument(projectId, hit.documentId());
            int refs = knowledgeService.sourceRefs(document.getId()).size();
            return new SearchHitResponse(hit.documentId(), document.getTitle(), hit.chunkId(),
                hit.ordinal(), hit.text(), hit.score(), refs);
          } catch (RuntimeException notVisible) {
            return null;
          }
        })
        .filter(Objects::nonNull)
        .toList();
  }

  @GetMapping("/{documentId}")
  public KnowledgeResponse get(
      @RequestHeader(value = USER_HEADER, required = false) String userId,
      @PathVariable String projectId,
      @PathVariable String documentId) {
    AppUser actor = authorizationService.requireActiveUser(userId);
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLE);
    return toResponse(knowledgeService.requireDocument(projectId, documentId));
  }

  @PostMapping("/{documentId}/submit-review")
  public KnowledgeResponse submitForReview(
      @RequestHeader(value = USER_HEADER, required = false) String userId,
      @PathVariable String projectId,
      @PathVariable String documentId) {
    AppUser actor = authorizationService.requireActiveUser(userId);
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLE);
    return toResponse(knowledgeService.submitForReview(actor, projectId, documentId));
  }

  @PostMapping("/{documentId}/verify")
  public KnowledgeResponse verify(
      @RequestHeader(value = USER_HEADER, required = false) String userId,
      @PathVariable String projectId,
      @PathVariable String documentId) {
    AppUser actor = authorizationService.requireActiveUser(userId);
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLE);
    return toResponse(knowledgeService.verify(actor, projectId, documentId));
  }

  private KnowledgeResponse toResponse(KnowledgeDocument document) {
    List<SourceRefResponse> refs = knowledgeService.sourceRefs(document.getId()).stream()
        .map(ref -> new SourceRefResponse(ref.getArtifactId(), ref.getLocator(), ref.getNote()))
        .toList();
    return new KnowledgeResponse(
        document.getId(), document.getScopeId(), document.getDocType(), document.getTitle(),
        document.getBody(), document.getStatus(), document.getVersion(), document.getCreatedBy(),
        document.getReviewer(), document.getReviewedAt(), document.getCreatedAt(),
        document.getUpdatedAt(), refs);
  }
}
