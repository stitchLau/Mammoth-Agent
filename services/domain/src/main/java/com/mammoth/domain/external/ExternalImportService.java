package com.mammoth.domain.external;

import tools.jackson.databind.ObjectMapper;
import com.mammoth.domain.artifact.ArtifactRecord;
import com.mammoth.domain.artifact.ArtifactService;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.service.AuditService;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.solution.AcceptanceCriterionRepository;
import com.mammoth.domain.support.Ids;
import com.mammoth.domain.support.JsonLite;
import com.mammoth.domain.web.ApiException;
import com.mammoth.domain.workitem.RequirementEvidence;
import com.mammoth.domain.workitem.WorkItem;
import com.mammoth.domain.workitem.WorkItemService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only external import (ADR-0011): file -> batch/records -> human mapping to requirement
 * evidence. Preview never persists; apply is transactional and idempotent per source/file hash.
 */
@Service
public class ExternalImportService {

  private static final Set<String> WRITE_ROLES = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR);
  private static final Set<String> ANY_ROLES = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR,
      AuthorizationService.REVIEWER, AuthorizationService.VIEWER);

  private final ExternalSourceRepository sourceRepository;
  private final ImportBatchRepository batchRepository;
  private final ExternalRecordRepository recordRepository;
  private final ExternalImportParser parser;
  private final ArtifactService artifactService;
  private final WorkItemService workItemService;
  private final AcceptanceCriterionRepository criterionRepository;
  private final AuthorizationService authorizationService;
  private final AuditService auditService;
  private final ObjectMapper mapper = new ObjectMapper();

  public ExternalImportService(
      ExternalSourceRepository sourceRepository,
      ImportBatchRepository batchRepository,
      ExternalRecordRepository recordRepository,
      ExternalImportParser parser,
      ArtifactService artifactService,
      WorkItemService workItemService,
      AcceptanceCriterionRepository criterionRepository,
      AuthorizationService authorizationService,
      AuditService auditService) {
    this.sourceRepository = sourceRepository;
    this.batchRepository = batchRepository;
    this.recordRepository = recordRepository;
    this.parser = parser;
    this.artifactService = artifactService;
    this.workItemService = workItemService;
    this.criterionRepository = criterionRepository;
    this.authorizationService = authorizationService;
    this.auditService = auditService;
  }

  public record SourceInput(String systemType, String name, String baseUrl) {
  }

  public record ImportResult(ImportBatch batch, boolean duplicate, int accepted, int rejected,
      List<ImportReport.RowError> errors) {
  }

  @Transactional
  public ExternalSource createSource(AppUser actor, String projectId, SourceInput input) {
    authorizationService.requireProjectRole(actor, projectId, WRITE_ROLES);
    String type = normalizeType(input.systemType());
    String name = requireText(input.name(), "source name");
    if (sourceRepository.existsByProjectIdAndName(projectId, name)) {
      throw ApiException.conflict("external source already exists: " + name);
    }
    ExternalSource source = new ExternalSource(Ids.newId(), projectId, type, name,
        sanitizeUrl(input.baseUrl()), actor.getId(), Ids.nowIso());
    sourceRepository.save(source);
    auditService.record(actor.getId(), "external_source.create", "external_source", source.getId(),
        JsonLite.write(JsonLite.object("systemType", type)));
    return source;
  }

  @Transactional(readOnly = true)
  public List<ExternalSource> sources(AppUser actor, String projectId) {
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLES);
    return sourceRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
  }

  @Transactional(readOnly = true)
  public ImportReport.Preview preview(AppUser actor, String projectId, String sourceId,
      String originalName, byte[] content, String mappingProfile) {
    authorizationService.requireProjectRole(actor, projectId, WRITE_ROLES);
    requireSource(projectId, sourceId);
    return ImportReport.preview(parser.parse(content, originalName, mappingProfile));
  }

  @Transactional
  public ImportResult apply(AppUser actor, String projectId, String sourceId, String originalName,
      String contentType, byte[] content, String mappingProfile) {
    authorizationService.requireProjectRole(actor, projectId, WRITE_ROLES);
    ExternalSource source = requireSource(projectId, sourceId);
    ImportReport.ParseResult parsed = parser.parse(content, originalName, mappingProfile);
    if (parsed.records().isEmpty()) {
      throw ApiException.badRequest("import has no valid rows"
          + (parsed.errors().isEmpty() ? "" : "; first error: " + parsed.errors().get(0).message()));
    }
    String fileSha256 = sha256(content);
    ImportBatch duplicate = batchRepository.findBySourceIdAndFileSha256(sourceId, fileSha256)
        .orElse(null);
    if (duplicate != null) {
      auditService.record(actor.getId(), "integration.import.reuse", "import_batch",
          duplicate.getId(), JsonLite.write(JsonLite.object("fileSha256", fileSha256)));
      return new ImportResult(duplicate, true, duplicate.getAcceptedCount(),
          duplicate.getRejectedCount(), List.of());
    }

    ArtifactRecord artifact = artifactService.upload(actor, projectId, "external_import",
        originalName == null || originalName.isBlank() ? "import.csv" : originalName,
        contentType == null || contentType.isBlank() ? "text/csv" : contentType,
        "external import for " + source.getName(), content);
    String now = Ids.nowIso();
    ImportBatch batch = new ImportBatch(Ids.newId(), projectId, sourceId, artifact.getId(),
        fileSha256, mappingProfile == null || mappingProfile.isBlank() ? "generic" : mappingProfile,
        ImportBatch.STATUS_APPLIED, parsed.rowCount(), 0, parsed.errors().size(),
        errorSummary(parsed.errors()), actor.getId(), now, now);
    batchRepository.save(batch);

    int accepted = 0;
    for (ImportedRecord imported : parsed.records()) {
      String contentHash = contentHash(imported);
      String version = (imported.updatedAt() == null ? "" : imported.updatedAt() + ":")
          + contentHash;
      if (recordRepository.existsBySourceIdAndExternalTypeAndExternalIdAndExternalVersion(
          sourceId, imported.externalType(), imported.externalId(), version)) {
        continue;
      }
      ExternalRecord record = new ExternalRecord(Ids.newId(), batch.getId(), sourceId, projectId,
          imported.externalType(), imported.externalId(), imported.externalKey(), version,
          imported.title(), imported.description(), sanitizeUrl(imported.url()), contentHash,
          metadata(imported), now);
      recordRepository.save(record);
      accepted++;
    }
    ImportBatch saved = batchRepository.save(new ImportBatch(batch.getId(), projectId, sourceId,
        artifact.getId(), fileSha256, batch.getMappingProfile(), ImportBatch.STATUS_APPLIED,
        parsed.rowCount(), accepted, parsed.errors().size(), batch.getErrorSummary(),
        actor.getId(), now, now));
    auditService.record(actor.getId(), "integration.import", "import_batch", saved.getId(),
        JsonLite.write(JsonLite.object(
            "sourceId", sourceId,
            "artifactId", artifact.getId(),
            "rows", parsed.rowCount(),
            "accepted", accepted,
            "rejected", parsed.errors().size())));
    return new ImportResult(saved, false, accepted, parsed.errors().size(), parsed.errors());
  }

  @Transactional(readOnly = true)
  public List<ImportBatch> batches(AppUser actor, String projectId) {
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLES);
    return batchRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
  }

  @Transactional(readOnly = true)
  public ImportBatch batch(AppUser actor, String projectId, String batchId) {
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLES);
    return batchRepository.findByIdAndProjectId(batchId, projectId)
        .orElseThrow(() -> ApiException.notFound("import batch not found: " + batchId));
  }

  @Transactional(readOnly = true)
  public List<ExternalRecord> records(AppUser actor, String projectId, String status) {
    authorizationService.requireProjectRole(actor, projectId, ANY_ROLES);
    return status == null || status.isBlank()
        ? recordRepository.findByProjectIdOrderByCreatedAtAsc(projectId)
        : recordRepository.findByProjectIdAndStatusOrderByCreatedAtAsc(projectId, status);
  }

  @Transactional
  public ExternalRecord map(AppUser actor, String projectId, String recordId, String workItemId,
      String criterionId) {
    authorizationService.requireProjectRole(actor, projectId, WRITE_ROLES);
    ExternalRecord record = requireRecord(projectId, recordId);
    if (!ExternalRecord.STATUS_UNMAPPED.equals(record.getStatus())) {
      throw ApiException.conflict("only unmapped external records can be mapped");
    }
    WorkItem item = workItemService.requireItem(projectId, workItemId);
    if (criterionId != null && !criterionId.isBlank()) {
      String versionId = item.getApprovedSolutionVersionId();
      if (versionId == null) {
        throw ApiException.conflict("criterion mapping requires an approved solution");
      }
      criterionRepository.findByIdAndSolutionVersionId(criterionId, versionId)
          .orElseThrow(() -> ApiException.badRequest(
              "acceptance criterion does not belong to the approved solution: " + criterionId));
    }
    ImportBatch batch = batchRepository.findByIdAndProjectId(record.getBatchId(), projectId)
        .orElseThrow(() -> ApiException.notFound("import batch not found"));
    String text = evidenceText(record);
    RequirementEvidence evidence = workItemService.addEvidence(actor, projectId, workItemId,
        ExternalSource.TYPE_JIRA.equals(sourceType(record.getSourceId())) ? RequirementEvidence.TYPE_JIRA
            : RequirementEvidence.TYPE_EXTERNAL,
        text, batch.getArtifactId(), locator(record), record.getCreatedAt());
    record.map(workItemId, evidence.getId(), criterionId == null || criterionId.isBlank()
        ? null : criterionId);
    recordRepository.save(record);
    auditService.record(actor.getId(), "external_record.map", "external_record", record.getId(),
        JsonLite.write(JsonLite.object("workItemId", workItemId,
            "evidenceId", evidence.getId(), "criterionId", criterionId)));
    return record;
  }

  @Transactional
  public ExternalRecord reject(AppUser actor, String projectId, String recordId, String note) {
    authorizationService.requireProjectRole(actor, projectId, WRITE_ROLES);
    ExternalRecord record = requireRecord(projectId, recordId);
    if (!ExternalRecord.STATUS_UNMAPPED.equals(record.getStatus())) {
      throw ApiException.conflict("only unmapped external records can be rejected");
    }
    record.reject(note == null || note.isBlank() ? "rejected by " + actor.getUsername()
        : note.trim());
    recordRepository.save(record);
    auditService.record(actor.getId(), "external_record.reject", "external_record", record.getId(),
        null);
    return record;
  }

  private ExternalSource requireSource(String projectId, String sourceId) {
    return sourceRepository.findByIdAndProjectId(sourceId, projectId)
        .orElseThrow(() -> ApiException.notFound("external source not found: " + sourceId));
  }

  private ExternalRecord requireRecord(String projectId, String recordId) {
    return recordRepository.findByIdAndProjectId(recordId, projectId)
        .orElseThrow(() -> ApiException.notFound("external record not found: " + recordId));
  }

  private String sourceType(String sourceId) {
    return sourceRepository.findById(sourceId).map(ExternalSource::getSystemType).orElse("generic");
  }

  private String evidenceText(ExternalRecord record) {
    StringBuilder text = new StringBuilder();
    text.append('[').append(record.getExternalKey() == null ? record.getExternalId()
        : record.getExternalKey()).append("] ").append(record.getTitle()).append("\n\n");
    text.append("来源：").append(record.getSourceUrlSanitized() == null ? "—"
        : record.getSourceUrlSanitized()).append('\n');
    text.append("版本：").append(record.getExternalVersion()).append('\n');
    if (record.getDescription() != null && !record.getDescription().isBlank()) {
      text.append('\n').append(record.getDescription()).append('\n');
    }
    return truncate(text.toString(), 8000);
  }

  private String locator(ExternalRecord record) {
    if (record.getSourceUrlSanitized() != null) {
      return record.getSourceUrlSanitized();
    }
    return "external:" + record.getExternalId();
  }

  private String metadata(ImportedRecord record) {
    try {
      return mapper.writeValueAsString(record.metadata());
    } catch (Exception error) {
      return "{}";
    }
  }

  private String contentHash(ImportedRecord record) {
    String value = String.join("\n",
        nullToEmpty(record.title()), nullToEmpty(record.description()),
        nullToEmpty(record.status()), nullToEmpty(record.type()),
        nullToEmpty(record.priority()), nullToEmpty(record.updatedAt()));
    return sha256(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String errorSummary(List<ImportReport.RowError> errors) {
    return truncate(errors.stream()
        .map(error -> "row " + error.row() + ": " + error.message())
        .reduce((left, right) -> left + "; " + right).orElse(null), 2000);
  }

  private static String sha256(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (Exception error) {
      throw new IllegalStateException("SHA-256 unavailable", error);
    }
  }

  private static String sanitizeUrl(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      URI uri = URI.create(value.trim());
      if (uri.getScheme() == null || uri.getHost() == null) {
        return null;
      }
      if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
        return null;
      }
      String path = uri.getPath() == null ? "" : uri.getPath();
      return uri.getScheme() + "://" + uri.getHost()
          + (uri.getPort() < 0 ? "" : ":" + uri.getPort()) + path;
    } catch (IllegalArgumentException error) {
      return null;
    }
  }

  private static String normalizeType(String type) {
    String value = type == null ? "" : type.trim().toLowerCase();
    if (!ExternalSource.TYPE_JIRA.equals(value) && !ExternalSource.TYPE_CSM.equals(value)
        && !ExternalSource.TYPE_GENERIC.equals(value)) {
      throw ApiException.badRequest("unsupported external source type: " + type);
    }
    return value;
  }

  private static String requireText(String value, String field) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isEmpty()) {
      throw ApiException.badRequest(field + " must not be empty");
    }
    return normalized;
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static String truncate(String value, int max) {
    return value == null || value.length() <= max ? value : value.substring(0, max);
  }
}
