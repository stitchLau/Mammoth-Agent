package com.mammoth.domain.external;

import com.mammoth.domain.config.JiraProperties;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.service.AuditService;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.support.JsonLite;
import com.mammoth.domain.web.ApiException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Jira Data Center read-only pull into the existing S3 import pipeline. Issues are evidence
 * candidates only; a human maps each record to a Mammoth requirement. There is no Jira write path.
 */
@Service
public class JiraImportService {

  private static final Set<String> WRITE_ROLES = Set.of(
      AuthorizationService.OWNER, AuthorizationService.EDITOR);

  private final ExternalImportService importService;
  private final ExternalSourceRepository sourceRepository;
  private final JiraClient client;
  private final JiraProperties properties;
  private final AuditService auditService;
  private final AuthorizationService authorizationService;
  private final ObjectMapper mapper = new ObjectMapper();

  public JiraImportService(ExternalImportService importService,
      ExternalSourceRepository sourceRepository, JiraClient client, JiraProperties properties,
      AuditService auditService, AuthorizationService authorizationService) {
    this.importService = importService;
    this.sourceRepository = sourceRepository;
    this.client = client;
    this.properties = properties;
    this.auditService = auditService;
    this.authorizationService = authorizationService;
  }

  @Transactional
  public ImportReport.Preview preview(AppUser actor, String projectId, String sourceId, String jql,
      Integer maxResults) {
    Pull pull = pull(actor, projectId, sourceId, jql, maxResults);
    ImportReport.Preview preview = importService.preview(actor, projectId, sourceId,
        "jira-api.json", pull.content(), "generic");
    audit(actor, sourceId, pull, preview.rowCount());
    return preview;
  }

  @Transactional
  public ExternalImportService.ImportResult apply(AppUser actor, String projectId, String sourceId,
      String jql, Integer maxResults) {
    Pull pull = pull(actor, projectId, sourceId, jql, maxResults);
    ExternalImportService.ImportResult result = importService.apply(actor, projectId, sourceId,
        "jira-api.json", "application/json", pull.content(), "generic");
    audit(actor, sourceId, pull, result.accepted());
    return result;
  }

  private Pull pull(AppUser actor, String projectId, String sourceId, String jql,
      Integer maxResults) {
    authorizationService.requireProjectRole(actor, projectId, WRITE_ROLES);
    ExternalSource source = sourceRepository.findByIdAndProjectId(sourceId, projectId)
        .orElseThrow(() -> ApiException.notFound("external source not found: " + sourceId));
    if (!ExternalSource.TYPE_JIRA.equals(source.getSystemType())) {
      throw ApiException.badRequest("source is not a Jira source: " + sourceId);
    }
    // The token is only ever sent to the configured Jira origin; a project source can add a
    // display URL but cannot redirect the PAT to another host.
    String configuredBase = properties.getBaseUrl();
    if (configuredBase == null || configuredBase.isBlank()) {
      throw ApiException.conflict("Jira base URL is not configured");
    }
    String sourceBase = source.getBaseUrlSanitized();
    if (sourceBase != null && !sourceBase.isBlank() && !sameOrigin(sourceBase, configuredBase)) {
      throw ApiException.badRequest("source base URL must match the configured Jira origin");
    }
    String baseUrl = configuredBase;
    String token = properties.getToken();
    if (token == null || token.isBlank()) {
      throw ApiException.conflict("Jira token is not configured");
    }
    int limit = maxResults == null || maxResults <= 0 ? properties.getMaxResults() : maxResults;
    limit = Math.max(1, Math.min(limit, 500));
    String effectiveJql = jql == null || jql.isBlank() ? properties.getJql() : jql.trim();
    List<JiraIssue> issues = client.search(baseUrl, token, effectiveJql, limit,
        properties.getPageSize());
    byte[] content = canonicalJson(issues);
    return new Pull(content, issues.size(), effectiveJql, limit);
  }

  private byte[] canonicalJson(List<JiraIssue> issues) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (JiraIssue issue : issues) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("externalId", issue.key());
      row.put("externalKey", issue.key());
      row.put("externalType", "issue");
      row.put("title", issue.summary());
      row.put("description", issue.description());
      row.put("status", issue.status());
      row.put("type", issue.issueType());
      row.put("priority", issue.priority());
      row.put("createdAt", issue.created());
      row.put("updatedAt", issue.updated());
      row.put("url", issue.url());
      row.put("assignee", issue.assignee());
      row.put("reporter", issue.reporter());
      row.put("component", issue.component());
      row.put("jiraProjectKey", issue.projectKey());
      rows.add(row);
    }
    try {
      return mapper.writeValueAsBytes(rows);
    } catch (Exception error) {
      throw new IllegalStateException("cannot serialize Jira issues", error);
    }
  }

  private void audit(AppUser actor, String sourceId, Pull pull, int resultCount) {
    auditService.record(actor.getId(), "integration.jira.read", "external_source", sourceId,
        JsonLite.write(JsonLite.object(
            "jqlLength", pull.jql().length(),
            "resultCount", resultCount,
            "maxResults", pull.limit())));
  }

  private record Pull(byte[] content, int issueCount, String jql, int limit) {
  }

  private static boolean sameOrigin(String left, String right) {
    try {
      URI a = URI.create(left);
      URI b = URI.create(right);
      return a.getScheme() != null && a.getHost() != null
          && a.getScheme().equalsIgnoreCase(b.getScheme())
          && a.getHost().equalsIgnoreCase(b.getHost())
          && effectivePort(a) == effectivePort(b);
    } catch (IllegalArgumentException error) {
      return false;
    }
  }

  private static int effectivePort(URI uri) {
    if (uri.getPort() >= 0) {
      return uri.getPort();
    }
    return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
  }
}
