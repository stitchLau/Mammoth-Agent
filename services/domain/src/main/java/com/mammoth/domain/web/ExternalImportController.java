package com.mammoth.domain.web;

import com.mammoth.domain.external.ExternalImportService;
import com.mammoth.domain.external.ExternalRecord;
import com.mammoth.domain.external.ExternalSource;
import com.mammoth.domain.external.ImportBatch;
import com.mammoth.domain.external.ImportReport;
import com.mammoth.domain.notification.NotificationService;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.security.CurrentUser;
import com.mammoth.domain.service.AuthorizationService;
import java.io.IOException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Read-only external import API (ADR-0011): sources, preview/apply, map/reject. */
@RestController
@RequestMapping("/api/projects/{projectId}")
public class ExternalImportController {

  private final ExternalImportService importService;
  private final AuthorizationService authorizationService;
  private final NotificationService notificationService;

  public ExternalImportController(ExternalImportService importService,
      AuthorizationService authorizationService, NotificationService notificationService) {
    this.importService = importService;
    this.authorizationService = authorizationService;
    this.notificationService = notificationService;
  }

  public record SourceRequest(String systemType, String name, String baseUrl) {
  }

  public record SourceResponse(String id, String systemType, String name, String baseUrl,
      String status, String createdBy, String createdAt) {

    static SourceResponse from(ExternalSource source) {
      return new SourceResponse(source.getId(), source.getSystemType(), source.getName(),
          source.getBaseUrlSanitized(), source.getStatus(), source.getCreatedBy(),
          source.getCreatedAt());
    }
  }

  public record ImportRequest(String sourceId, String mappingProfile) {
  }

  public record BatchResponse(String id, String projectId, String sourceId, String artifactId,
      String fileSha256, String mappingProfile, String status, int rowCount, int acceptedCount,
      int rejectedCount, String errorSummary, String createdBy, String createdAt, String finishedAt) {

    static BatchResponse from(ImportBatch batch) {
      return new BatchResponse(batch.getId(), batch.getProjectId(), batch.getSourceId(),
          batch.getArtifactId(), batch.getFileSha256(), batch.getMappingProfile(), batch.getStatus(),
          batch.getRowCount(), batch.getAcceptedCount(), batch.getRejectedCount(),
          batch.getErrorSummary(), batch.getCreatedBy(), batch.getCreatedAt(),
          batch.getFinishedAt());
    }
  }

  public record RowErrorResponse(int row, String message) {
  }

  public record PreviewResponse(int rowCount, int acceptedCount, int rejectedCount,
      List<RowErrorResponse> errors, List<RecordResponse> sample) {
  }

  public record RecordResponse(String id, String batchId, String externalType, String externalId,
      String externalKey, String externalVersion, String title, String description, String status,
      String sourceUrl, String contentHash, String mappedWorkItemId, String mappedEvidenceId,
      String mappedCriterionId, String note, String createdAt) {

    static RecordResponse from(ExternalRecord record) {
      return new RecordResponse(record.getId(), record.getBatchId(), record.getExternalType(),
          record.getExternalId(), record.getExternalKey(), record.getExternalVersion(),
          record.getTitle(), record.getDescription(), record.getStatus(),
          record.getSourceUrlSanitized(), record.getContentHash(), record.getMappedWorkItemId(),
          record.getMappedEvidenceId(), record.getMappedCriterionId(), record.getNote(),
          record.getCreatedAt());
    }
  }

  public record ImportResponse(BatchResponse batch, boolean duplicate, int accepted, int rejected,
      List<RowErrorResponse> errors) {
  }

  public record MapRequest(String workItemId, String criterionId) {
  }

  public record RejectRequest(String note) {
  }

  @PostMapping("/external-sources")
  public SourceResponse createSource(@PathVariable String projectId,
      @RequestBody SourceRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return SourceResponse.from(importService.createSource(actor, projectId,
        new ExternalImportService.SourceInput(request.systemType(), request.name(),
            request.baseUrl())));
  }

  @GetMapping("/external-sources")
  public List<SourceResponse> sources(@PathVariable String projectId) {
    AppUser actor = CurrentUser.require(authorizationService);
    return importService.sources(actor, projectId).stream().map(SourceResponse::from).toList();
  }

  @PostMapping("/imports/preview")
  public PreviewResponse preview(@PathVariable String projectId,
      @RequestPart("file") MultipartFile file,
      @RequestParam("sourceId") String sourceId,
      @RequestParam(value = "mappingProfile", required = false) String mappingProfile)
      throws IOException {
    AppUser actor = CurrentUser.require(authorizationService);
    ImportReport.Preview preview = importService.preview(actor, projectId, sourceId,
        file.getOriginalFilename(), file.getBytes(), mappingProfile);
    return new PreviewResponse(preview.rowCount(), preview.acceptedCount(), preview.rejectedCount(),
        preview.errors().stream().map(error -> new RowErrorResponse(error.row(), error.message()))
            .toList(),
        preview.sample().stream().map(record -> new RecordResponse(null, null,
            record.externalType(), record.externalId(), record.externalKey(), "",
            record.title(), record.description(), "preview", record.url(), "", null, null, null,
            null, null)).toList());
  }

  @PostMapping("/imports")
  public ImportResponse apply(@PathVariable String projectId,
      @RequestPart("file") MultipartFile file,
      @RequestParam("sourceId") String sourceId,
      @RequestParam(value = "mappingProfile", required = false) String mappingProfile)
      throws IOException {
    AppUser actor = CurrentUser.require(authorizationService);
    ExternalImportService.ImportResult result;
    try {
      result = importService.apply(actor, projectId, sourceId,
          file.getOriginalFilename(), file.getContentType(), file.getBytes(), mappingProfile);
    } catch (com.mammoth.domain.web.ApiException error) {
      notificationService.importFailed(actor, projectId, sourceId, error.getMessage());
      throw error;
    }
    return new ImportResponse(BatchResponse.from(result.batch()), result.duplicate(),
        result.accepted(), result.rejected(),
        result.errors().stream().map(error -> new RowErrorResponse(error.row(), error.message()))
            .toList());
  }

  @GetMapping("/imports")
  public List<BatchResponse> batches(@PathVariable String projectId) {
    AppUser actor = CurrentUser.require(authorizationService);
    return importService.batches(actor, projectId).stream().map(BatchResponse::from).toList();
  }

  @GetMapping("/imports/{batchId}")
  public BatchResponse batch(@PathVariable String projectId, @PathVariable String batchId) {
    AppUser actor = CurrentUser.require(authorizationService);
    return BatchResponse.from(importService.batch(actor, projectId, batchId));
  }

  @GetMapping("/external-records")
  public List<RecordResponse> records(@PathVariable String projectId,
      @RequestParam(value = "status", required = false) String status) {
    AppUser actor = CurrentUser.require(authorizationService);
    return importService.records(actor, projectId, status).stream().map(RecordResponse::from)
        .toList();
  }

  @PostMapping("/external-records/{recordId}/map")
  public RecordResponse map(@PathVariable String projectId, @PathVariable String recordId,
      @RequestBody MapRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return RecordResponse.from(importService.map(actor, projectId, recordId, request.workItemId(),
        request.criterionId()));
  }

  @PostMapping("/external-records/{recordId}/reject")
  public RecordResponse reject(@PathVariable String projectId, @PathVariable String recordId,
      @RequestBody RejectRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return RecordResponse.from(importService.reject(actor, projectId, recordId, request.note()));
  }
}
