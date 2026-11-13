package com.mammoth.domain.web;

import com.mammoth.domain.knowledge.KnowledgeStalenessService;
import com.mammoth.domain.knowledge.KnowledgeDocument;
import com.mammoth.domain.knowledge.KnowledgeSearchAdapter;
import com.mammoth.domain.knowledge.KnowledgeService;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.security.CurrentUser;
import com.mammoth.domain.service.AuthorizationService;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Knowledge document lifecycle and project-scoped search (ADR-0005). */
@RestController
@RequestMapping("/api/projects/{projectId}/knowledge")
public class KnowledgeController {

  private static final Set<String> ANY_ROLE = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR,
      AuthorizationService.REVIEWER, AuthorizationService.VIEWER);
  private static final Set<String> EDIT_ROLES = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR);

  private final KnowledgeService knowledgeService;
  private final AuthorizationService authorizationService;
  private final KnowledgeSearchAdapter searchAdapter;
  private final KnowledgeStalenessService stalenessService;

  public KnowledgeController(
      KnowledgeService knowledgeService,
      AuthorizationService authorizationService,
      KnowledgeSearchAdapter searchAdapter,
      KnowledgeStalenessService stalenessService) {
    this.knowledgeService = knowledgeService;
    this.authorizationService = authorizationService;
    this.searchAdapter = searchAdapter;
    this.stalenessService = stalenessService;
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
      String reviewedAt, String applicableBaselineId, boolean stale, String supersededBy,
      String deprecationNote, String createdAt, String updatedAt,
      List<SourceRefResponse> sourceRefs) {
  }

  public record SearchHitResponse(String documentId, String title, String chunkId, int ordinal,
      String text, double score, int sourceRefCount, String applicableBaselineId,
      boolean stale) {
  }

  @PostMapping
  public KnowledgeResponse create(@PathVariable String projectId,
      @RequestBody CreateKnowledgeRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    authorizationService.requireProjectRole(actor, projectId, EDIT_ROLES);
    List<KnowledgeService.SourceRefInput> refs = request.sourceRefs() == null
        ? List.of()
        : request.sourceRefs().stream()
            .map(ref -> new KnowledgeService.SourceRefInput(ref.artifactId(), ref.locator(), ref.note()))
            .toList();
    return toResponse(knowledgeService.create(
        actor, projectId, request.docType(), request.title(), request.body(), refs));
  }

  @GetMapping
  public List<KnowledgeResponse> list(@PathVariable String projectId,
      @RequestParam(value = "status", required = false) String status) {
    AppUser actor = CurrentUser.require(authorizationService);
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLE);
    return knowledgeService.list(projectId, status).stream().map(this::toResponse).toList();
  }

  @GetMapping("/search")
  public List<SearchHitResponse> search(@PathVariable String projectId,
      @RequestParam("q") String query,
      @RequestParam(value = "topK", defaultValue = "5") int topK) {
    AppUser actor = CurrentUser.require(authorizationService);
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLE);
    return searchAdapter.search(projectId, query, topK).stream()
        .map(hit -> {
          try {
            KnowledgeDocument document = knowledgeService.requireDocument(projectId, hit.documentId());
            return new SearchHitResponse(hit.documentId(), document.getTitle(), hit.chunkId(),
                hit.ordinal(), hit.text(), hit.score(),
                knowledgeService.sourceRefs(document.getId()).size(),
                document.getApplicableBaselineId(), stalenessService.isStale(document));
          } catch (RuntimeException notVisible) {
            return null;
          }
        })
        .filter(Objects::nonNull)
        .toList();
  }

  @GetMapping("/{documentId}")
  public KnowledgeResponse get(@PathVariable String projectId, @PathVariable String documentId) {
    AppUser actor = CurrentUser.require(authorizationService);
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLE);
    return toResponse(knowledgeService.requireDocument(projectId, documentId));
  }

  @PutMapping("/{documentId}")
  public KnowledgeResponse updateDraft(@PathVariable String projectId,
      @PathVariable String documentId, @RequestBody CreateKnowledgeRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    authorizationService.requireProjectRole(actor, projectId, EDIT_ROLES);
    List<KnowledgeService.SourceRefInput> refs = request.sourceRefs() == null
        ? List.of()
        : request.sourceRefs().stream()
            .map(ref -> new KnowledgeService.SourceRefInput(ref.artifactId(), ref.locator(), ref.note()))
            .toList();
    return toResponse(knowledgeService.updateDraft(
        actor, projectId, documentId, request.docType(), request.title(), request.body(), refs));
  }

  @PostMapping("/{documentId}/submit-review")
  public KnowledgeResponse submitForReview(@PathVariable String projectId,
      @PathVariable String documentId) {
    AppUser actor = CurrentUser.require(authorizationService);
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLE);
    return toResponse(knowledgeService.submitForReview(actor, projectId, documentId));
  }

  @PostMapping("/{documentId}/verify")
  public KnowledgeResponse verify(@PathVariable String projectId, @PathVariable String documentId) {
    AppUser actor = CurrentUser.require(authorizationService);
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLE);
    return toResponse(knowledgeService.verify(actor, projectId, documentId));
  }

  public record SupersedeRequest(String replacementDocumentId) {
  }

  public record DeprecateRequest(String note) {
  }

  @PostMapping("/{documentId}/supersede")
  public KnowledgeResponse supersede(@PathVariable String projectId,
      @PathVariable String documentId, @RequestBody SupersedeRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return toResponse(knowledgeService.supersede(actor, projectId, documentId,
        request.replacementDocumentId()));
  }

  @PostMapping("/{documentId}/deprecate")
  public KnowledgeResponse deprecate(@PathVariable String projectId,
      @PathVariable String documentId, @RequestBody(required = false) DeprecateRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return toResponse(knowledgeService.deprecate(actor, projectId, documentId,
        request == null ? null : request.note()));
  }
  private KnowledgeResponse toResponse(KnowledgeDocument document) {
    List<SourceRefResponse> refs = knowledgeService.sourceRefs(document.getId()).stream()
        .map(ref -> new SourceRefResponse(ref.getArtifactId(), ref.getLocator(), ref.getNote()))
        .toList();
    return new KnowledgeResponse(
        document.getId(), document.getScopeId(), document.getDocType(), document.getTitle(),
        document.getBody(), document.getStatus(), document.getVersion(), document.getCreatedBy(),
        document.getReviewer(), document.getReviewedAt(), document.getApplicableBaselineId(),
        stalenessService.isStale(document), document.getSupersededBy(),
        document.getDeprecationNote(), document.getCreatedAt(), document.getUpdatedAt(), refs);
  }

}
