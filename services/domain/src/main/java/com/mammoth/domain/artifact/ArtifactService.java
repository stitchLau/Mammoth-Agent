package com.mammoth.domain.artifact;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.service.AuditService;
import com.mammoth.domain.support.Ids;
import com.mammoth.domain.web.ApiException;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Immutable artifact ingestion (ADR-0004): bytes are content-addressed and written once, metadata
 * is per project, duplicates inside one project reuse the existing record idempotently.
 */
@Service
public class ArtifactService {

  /** Roles allowed to add artifacts (owner/editor per ADR-0006). */
  public static final Set<String> WRITE_ROLES =
      Set.of(com.mammoth.domain.service.AuthorizationService.OWNER,
          com.mammoth.domain.service.AuthorizationService.EDITOR);

  private final ArtifactRepository artifactRepository;
  private final ArtifactStore artifactStore;
  private final AuditService auditService;

  public ArtifactService(
      ArtifactRepository artifactRepository,
      ArtifactStore artifactStore,
      AuditService auditService) {
    this.artifactRepository = artifactRepository;
    this.artifactStore = artifactStore;
    this.auditService = auditService;
  }

  @Transactional
  public ArtifactRecord upload(AppUser actor, String projectId, String kind, String originalName,
      String mime, String sourceNote, byte[] content) {
    if (content == null || content.length == 0) {
      throw ApiException.badRequest("artifact content must not be empty");
    }
    if (originalName == null || originalName.isBlank()) {
      throw ApiException.badRequest("artifact original name must not be empty");
    }
    ArtifactStore.StoredArtifact stored = artifactStore.put(content);
    var existing = artifactRepository.findByProjectIdAndContentHash(projectId, stored.sha256());
    if (existing.isPresent()) {
      auditService.record(actor.getId(), "artifact.reuse", "artifact", existing.get().getId(), null);
      return existing.get();
    }
    ArtifactRecord record = new ArtifactRecord(
        Ids.newId(),
        projectId,
        kind == null || kind.isBlank() ? "source" : kind,
        originalName.trim(),
        stored.sha256(),
        stored.relativePath(),
        stored.sizeBytes(),
        mime,
        sourceNote,
        actor.getId(),
        Ids.nowIso());
    artifactRepository.save(record);
    auditService.record(actor.getId(), "artifact.add", "artifact", record.getId(), null);
    return record;
  }

  @Transactional(readOnly = true)
  public List<ArtifactRecord> list(String projectId) {
    return artifactRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
  }

  @Transactional(readOnly = true)
  public ArtifactRecord require(String projectId, String artifactId) {
    return artifactRepository.findById(artifactId)
        .filter(record -> record.getProjectId().equals(projectId))
        .orElseThrow(() -> ApiException.notFound("artifact not found: " + artifactId));
  }

  /** Read verified bytes for download. */
  @Transactional(readOnly = true)
  public byte[] content(ArtifactRecord record) {
    return artifactStore.read(record.getContentHash());
  }
}
