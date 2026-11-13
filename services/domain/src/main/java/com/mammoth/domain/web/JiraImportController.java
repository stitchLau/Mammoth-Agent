package com.mammoth.domain.web;

import com.mammoth.domain.external.ExternalImportService;
import com.mammoth.domain.external.ImportBatch;
import com.mammoth.domain.external.ImportReport;
import com.mammoth.domain.external.ImportedRecord;
import com.mammoth.domain.external.JiraImportService;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.security.CurrentUser;
import com.mammoth.domain.service.AuthorizationService;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Jira Data Center read-only pull (M4.5-1); only issue search, never writes to Jira. */
@RestController
@RequestMapping("/api/projects/{projectId}/external-sources/{sourceId}/jira")
public class JiraImportController {

  private final JiraImportService jiraImportService;
  private final AuthorizationService authorizationService;

  public JiraImportController(JiraImportService jiraImportService,
      AuthorizationService authorizationService) {
    this.jiraImportService = jiraImportService;
    this.authorizationService = authorizationService;
  }

  public record PullRequest(String jql, Integer maxResults) {
  }

  public record RowError(int row, String message) {
  }

  public record PreviewResponse(int rowCount, int acceptedCount, int rejectedCount,
      List<RowError> errors, List<ImportedRecord> sample) {
  }

  public record ImportResponse(ImportBatch batch, boolean duplicate, int accepted, int rejected,
      List<RowError> errors) {
  }

  @PostMapping("/preview")
  public PreviewResponse preview(@PathVariable String projectId, @PathVariable String sourceId,
      @RequestBody(required = false) PullRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    ImportReport.Preview preview = jiraImportService.preview(actor, projectId, sourceId,
        request == null ? null : request.jql(), request == null ? null : request.maxResults());
    return new PreviewResponse(preview.rowCount(), preview.acceptedCount(), preview.rejectedCount(),
        preview.errors().stream().map(error -> new RowError(error.row(), error.message())).toList(),
        preview.sample());
  }

  @PostMapping("/import")
  public ImportResponse apply(@PathVariable String projectId, @PathVariable String sourceId,
      @RequestBody(required = false) PullRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    ExternalImportService.ImportResult result = jiraImportService.apply(actor, projectId, sourceId,
        request == null ? null : request.jql(), request == null ? null : request.maxResults());
    return new ImportResponse(result.batch(), result.duplicate(), result.accepted(),
        result.rejected(),
        result.errors().stream().map(error -> new RowError(error.row(), error.message())).toList());
  }
}
