package com.mammoth.domain.knowledge;

import com.mammoth.domain.artifact.ArtifactService;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.ProjectMember;
import com.mammoth.domain.persistence.ProjectMemberRepository;
import com.mammoth.domain.notification.NotificationService;
import com.mammoth.domain.service.AuditService;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.support.Ids;
import com.mammoth.domain.web.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Knowledge lifecycle for M1 (ADR-0005, docs/06 §3):
 * {@code draft -> needs_review -> verified}; AI-generated text can never be verified without a
 * different human reviewer, and every document must cite at least one artifact.
 */
@Service
public class KnowledgeService {

  private static final String SCOPE_PROJECT = "project";
  private static final int CHUNK_SIZE = 200;
  private static final int CHUNK_OVERLAP = 40;
  private static final Set<String> EDIT_ROLES =
      Set.of(AuthorizationService.OWNER, AuthorizationService.EDITOR);
  private static final Set<String> REVIEW_ROLES =
      Set.of(AuthorizationService.OWNER, AuthorizationService.REVIEWER);

  private final KnowledgeDocumentRepository documentRepository;
  private final KnowledgeSourceRefRepository sourceRefRepository;
  private final KnowledgeChunkRepository chunkRepository;
  private final ArtifactService artifactService;
  private final ProjectMemberRepository projectMemberRepository;
  private final AuditService auditService;
  private final NotificationService notificationService;

  public KnowledgeService(
      KnowledgeDocumentRepository documentRepository,
      KnowledgeSourceRefRepository sourceRefRepository,
      KnowledgeChunkRepository chunkRepository,
      ArtifactService artifactService,
      ProjectMemberRepository projectMemberRepository,
      AuditService auditService,
      NotificationService notificationService) {
    this.documentRepository = documentRepository;
    this.sourceRefRepository = sourceRefRepository;
    this.chunkRepository = chunkRepository;
    this.artifactService = artifactService;
    this.projectMemberRepository = projectMemberRepository;
    this.auditService = auditService;
    this.notificationService = notificationService;
  }

  /** One evidence reference supplied by the caller. */
  public record SourceRefInput(String artifactId, String locator, String note) {
  }

  @Transactional
  public KnowledgeDocument create(AppUser actor, String projectId, String docType, String title,
      String body, List<SourceRefInput> sourceRefs) {
    requireEditorRole(actor, projectId);
    String normalizedTitle = requireText(title, "title");
    String normalizedBody = requireText(body, "body");
    if (sourceRefs == null || sourceRefs.isEmpty()) {
      throw ApiException.badRequest("knowledge document must cite at least one artifact");
    }
    String now = Ids.nowIso();
    KnowledgeDocument document = new KnowledgeDocument(
        Ids.newId(), SCOPE_PROJECT, projectId,
        docType == null || docType.isBlank() ? "note" : docType.trim(),
        normalizedTitle, normalizedBody, KnowledgeDocument.DRAFT, 1, actor.getId(), now);
    documentRepository.save(document);

    for (SourceRefInput ref : sourceRefs) {
      if (ref == null || ref.artifactId() == null || ref.artifactId().isBlank()) {
        throw ApiException.badRequest("source reference requires an artifactId");
      }
      // Enforces that the referenced artifact belongs to the same project.
      artifactService.require(projectId, ref.artifactId());
      sourceRefRepository.save(new KnowledgeSourceRef(
          Ids.newId(), document.getId(), ref.artifactId(), ref.locator(), ref.note()));
    }
    reindex(document);
    auditService.record(actor.getId(), "knowledge.create", "knowledge_document", document.getId(), null);
    return document;
  }

  /**
   * Delivery-loop draft: created from an approved delivery snapshot, citing its artifact and the
   * code baseline it was derived from (ADR-0007). It still needs normal human review.
   */
  @Transactional
  public KnowledgeDocument createDeliveryDraft(AppUser author, String projectId, String title,
      String body, String artifactId, String baselineId) {
    requireEditorRole(author, projectId);
    String normalizedTitle = requireText(title, "title");
    String normalizedBody = requireText(body, "body");
    artifactService.require(projectId, artifactId);
    String now = Ids.nowIso();
    KnowledgeDocument document = new KnowledgeDocument(Ids.newId(), SCOPE_PROJECT, projectId,
        "delivery", normalizedTitle, normalizedBody, KnowledgeDocument.DRAFT, 1, author.getId(),
        now);
    documentRepository.save(document);
    sourceRefRepository.save(new KnowledgeSourceRef(Ids.newId(), document.getId(), artifactId,
        "As-built", "交付快照"));
    reindex(document);
    document.attachBaseline(baselineId);
    documentRepository.save(document);
    auditService.record(author.getId(), "knowledge.create_delivery", "knowledge_document",
        document.getId(), com.mammoth.domain.support.JsonLite.write(
            com.mammoth.domain.support.JsonLite.object("artifactId", artifactId,
                "baselineId", baselineId)));
    return document;
  }

  /** Edit a draft in place; only draft documents can change and evidence refs are replaced. */
  @Transactional
  public KnowledgeDocument updateDraft(AppUser actor, String projectId, String documentId,
      String docType, String title, String body, List<SourceRefInput> sourceRefs) {
    KnowledgeDocument document = requireDocument(projectId, documentId);
    requireEditor(actor, projectId, document);
    if (!KnowledgeDocument.DRAFT.equals(document.getStatus())) {
      throw ApiException.conflict("only draft documents can be edited");
    }
    String normalizedTitle = requireText(title, "title");
    String normalizedBody = requireText(body, "body");
    if (sourceRefs == null || sourceRefs.isEmpty()) {
      throw ApiException.badRequest("knowledge document must cite at least one artifact");
    }
    document.updateDraft(
        docType == null || docType.isBlank() ? "note" : docType.trim(),
        normalizedTitle, normalizedBody, Ids.nowIso());
    documentRepository.save(document);

    sourceRefRepository.deleteByKnowledgeDocumentId(documentId);
    sourceRefRepository.flush();
    for (SourceRefInput ref : sourceRefs) {
      if (ref == null || ref.artifactId() == null || ref.artifactId().isBlank()) {
        throw ApiException.badRequest("source reference requires an artifactId");
      }
      artifactService.require(projectId, ref.artifactId());
      sourceRefRepository.save(new KnowledgeSourceRef(
          Ids.newId(), documentId, ref.artifactId(), ref.locator(), ref.note()));
    }
    reindex(document);
    auditService.record(actor.getId(), "knowledge.update", "knowledge_document", documentId, null);
    return document;
  }

  @Transactional
  public KnowledgeDocument submitForReview(AppUser actor, String projectId, String documentId) {
    KnowledgeDocument document = requireDocument(projectId, documentId);
    requireEditor(actor, projectId, document);
    if (!KnowledgeDocument.DRAFT.equals(document.getStatus())) {
      throw ApiException.conflict("only draft documents can be submitted for review");
    }
    if (sourceRefRepository.countByKnowledgeDocumentId(documentId) == 0) {
      throw ApiException.badRequest("knowledge document has no source references");
    }
    document.submitForReview();
    documentRepository.save(document);
    auditService.record(actor.getId(), "knowledge.submit_review", "knowledge_document", documentId, null);
    notificationService.knowledgeReviewRequested(actor, projectId, documentId, document.getTitle());
    return document;
  }

  /** Human review gate: the reviewer must differ from the creator (four-eyes). */
  @Transactional
  public KnowledgeDocument verify(AppUser actor, String projectId, String documentId) {
    KnowledgeDocument document = requireDocument(projectId, documentId);
    if (!KnowledgeDocument.NEEDS_REVIEW.equals(document.getStatus())) {
      throw ApiException.conflict("only documents in needs_review can be verified");
    }
    if (actor.getId().equals(document.getCreatedBy())) {
      throw ApiException.forbidden("the creator cannot verify their own knowledge document");
    }
    ProjectMember membership = projectMemberRepository
        .findByProjectIdAndUserId(projectId, actor.getId())
        .orElseThrow(() -> ApiException.forbidden("no access to project " + projectId));
    if (!REVIEW_ROLES.contains(membership.getRole())) {
      throw ApiException.forbidden(
          "role " + membership.getRole() + " cannot verify knowledge (owner or reviewer required)");
    }
    String now = Ids.nowIso();
    document.verify(actor.getId(), now);
    documentRepository.save(document);
    auditService.record(actor.getId(), "knowledge.verify", "knowledge_document", documentId, null);
    return document;
  }

  /**
   * Mark a verified document as replaced by a newer verified document (docs/06 §7). Superseded
   * knowledge stays in the database for traceability but is no longer searchable.
   */
  @Transactional
  public KnowledgeDocument supersede(AppUser actor, String projectId, String documentId,
      String replacementDocumentId) {
    KnowledgeDocument document = requireDocument(projectId, documentId);
    requireReviewerRole(actor, projectId);
    if (!KnowledgeDocument.VERIFIED.equals(document.getStatus())) {
      throw ApiException.conflict("only verified knowledge can be superseded");
    }
    String replacementId = requireText(replacementDocumentId, "replacementDocumentId");
    if (replacementId.equals(documentId)) {
      throw ApiException.badRequest("a document cannot supersede itself");
    }
    KnowledgeDocument replacement = requireDocument(projectId, replacementId);
    if (!KnowledgeDocument.VERIFIED.equals(replacement.getStatus())) {
      throw ApiException.conflict("replacement document must be verified first");
    }
    document.supersede(replacementId, Ids.nowIso());
    documentRepository.save(document);
    auditService.record(actor.getId(), "knowledge.supersede", "knowledge_document", documentId,
        com.mammoth.domain.support.JsonLite.write(
            com.mammoth.domain.support.JsonLite.object("replacementDocumentId", replacementId)));
    return document;
  }

  /** Mark a verified document as deprecated; it remains readable for audit but is not searchable. */
  @Transactional
  public KnowledgeDocument deprecate(AppUser actor, String projectId, String documentId,
      String note) {
    KnowledgeDocument document = requireDocument(projectId, documentId);
    requireReviewerRole(actor, projectId);
    if (!KnowledgeDocument.VERIFIED.equals(document.getStatus())) {
      throw ApiException.conflict("only verified knowledge can be deprecated");
    }
    document.deprecate(note == null || note.isBlank() ? null : note.trim(), Ids.nowIso());
    documentRepository.save(document);
    auditService.record(actor.getId(), "knowledge.deprecate", "knowledge_document", documentId,
        null);
    return document;
  }

  private void requireReviewerRole(AppUser actor, String projectId) {
    ProjectMember membership = projectMemberRepository
        .findByProjectIdAndUserId(projectId, actor.getId())
        .orElseThrow(() -> ApiException.forbidden("no access to project " + projectId));
    if (!REVIEW_ROLES.contains(membership.getRole())) {
      throw ApiException.forbidden(
          "role " + membership.getRole() + " cannot change knowledge status (owner or reviewer required)");
    }
  }
  @Transactional(readOnly = true)
  public List<KnowledgeDocument> list(String projectId, String status) {
    if (status == null || status.isBlank()) {
      return documentRepository.findByScopeTypeAndScopeIdOrderByCreatedAtAsc(SCOPE_PROJECT, projectId);
    }
    return documentRepository.findByScopeTypeAndScopeIdAndStatusOrderByCreatedAtAsc(
        SCOPE_PROJECT, projectId, status);
  }

  @Transactional(readOnly = true)
  public KnowledgeDocument requireDocument(String projectId, String documentId) {
    return documentRepository.findByIdAndScopeTypeAndScopeId(documentId, SCOPE_PROJECT, projectId)
        .orElseThrow(() -> ApiException.notFound("knowledge document not found: " + documentId));
  }

  @Transactional(readOnly = true)
  public List<KnowledgeSourceRef> sourceRefs(String documentId) {
    return sourceRefRepository.findByKnowledgeDocumentIdOrderByIdAsc(documentId);
  }

  @Transactional(readOnly = true)
  public long chunkCount(String documentId) {
    return chunkRepository.countByKnowledgeDocumentId(documentId);
  }

  private void requireEditor(AppUser actor, String projectId, KnowledgeDocument document) {
    if (actor.getId().equals(document.getCreatedBy())) {
      return;
    }
    requireEditorRole(actor, projectId);
  }

  /** Creating or editing knowledge requires owner/editor on the project (defense in depth). */
  private void requireEditorRole(AppUser actor, String projectId) {
    ProjectMember membership = projectMemberRepository
        .findByProjectIdAndUserId(projectId, actor.getId())
        .orElseThrow(() -> ApiException.forbidden("no access to project " + projectId));
    if (!EDIT_ROLES.contains(membership.getRole())) {
      throw ApiException.forbidden("role " + membership.getRole() + " cannot edit knowledge");
    }
  }

  /** Rebuild derived chunks for a document; FTS rows are maintained by database triggers (V2). */
  void reindex(KnowledgeDocument document) {
    chunkRepository.deleteByDocumentId(document.getId());
    List<KnowledgeChunk> chunks = new ArrayList<>();
    String body = document.getBody();
    int start = 0;
    int ordinal = 0;
    while (start < body.length()) {
      int end = Math.min(start + CHUNK_SIZE, body.length());
      chunks.add(new KnowledgeChunk(Ids.newId(), document.getId(), ordinal, body.substring(start, end)));
      ordinal++;
      if (end == body.length()) {
        break;
      }
      start = Math.max(start + 1, end - CHUNK_OVERLAP);
    }
    chunkRepository.saveAll(chunks);
  }

  private static String requireText(String value, String field) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isEmpty()) {
      throw ApiException.badRequest(field + " must not be empty");
    }
    return normalized;
  }
}
