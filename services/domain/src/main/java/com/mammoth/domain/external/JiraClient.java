package com.mammoth.domain.external;

import com.mammoth.domain.web.ApiException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Jira Data Center REST API v2 read-only client (PAT via Bearer). It only calls issue search and
 * never writes to Jira. The token is never logged or included in an exception message.
 */
@Component
public class JiraClient {

  private static final String FIELDS =
      "summary,description,status,issuetype,priority,created,updated,assignee,reporter,"
          + "components,project";
  private static final int ABSOLUTE_MAX = 500;

  private final ObjectMapper mapper = new ObjectMapper();
  private final HttpClient client;

  public JiraClient() {
    this.client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }

  public List<JiraIssue> search(String baseUrl, String token, String jql, int maxResults,
      int pageSize) {
    if (token == null || token.isBlank()) {
      throw ApiException.conflict("Jira token is not configured");
    }
    String normalizedBase = requireHttpBase(baseUrl);
    String effectiveJql = jql == null || jql.isBlank()
        ? "updated >= -30d ORDER BY updated DESC" : jql.trim();
    int limit = Math.max(1, Math.min(maxResults, ABSOLUTE_MAX));
    int size = Math.max(1, Math.min(pageSize, 100));
    List<JiraIssue> issues = new ArrayList<>();
    Map<String, JiraIssue> seen = new LinkedHashMap<>();
    int startAt = 0;
    while (issues.size() < limit) {
      int requestSize = Math.min(size, limit - issues.size());
      JsonNode page = fetchPage(normalizedBase, token, effectiveJql, startAt, requestSize);
      JsonNode array = page.get("issues");
      if (array == null || !array.isArray()) {
        throw ApiException.badRequest("Jira search response has no issues array");
      }
      if (array.isEmpty()) {
        break;
      }
      for (JsonNode node : array) {
        JiraIssue issue = parseIssue(normalizedBase, node);
        if (issue != null && seen.putIfAbsent(issue.key(), issue) == null) {
          issues.add(issue);
          if (issues.size() >= limit) {
            break;
          }
        }
      }
      startAt += array.size();
      int total = page.path("total").asInt(startAt);
      if (startAt >= total || array.size() < requestSize) {
        break;
      }
    }
    return issues;
  }

  private JsonNode fetchPage(String baseUrl, String token, String jql, int startAt, int size) {
    try {
      String query = "jql=" + URLEncoder.encode(jql, StandardCharsets.UTF_8)
          + "&startAt=" + startAt + "&maxResults=" + size + "&fields="
          + URLEncoder.encode(FIELDS, StandardCharsets.UTF_8);
      URI uri = URI.create(baseUrl + "/rest/api/2/search?" + query);
      HttpRequest request = HttpRequest.newBuilder(uri)
          .timeout(Duration.ofSeconds(20))
          .header("Accept", "application/json")
          .header("Authorization", "Bearer " + token)
          .GET()
          .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      int status = response.statusCode();
      if (status == 401 || status == 403) {
        throw ApiException.forbidden("Jira authentication or permission failed (HTTP " + status + ")");
      }
      if (status == 429) {
        throw ApiException.conflict("Jira rate limit reached; retry later");
      }
      if (status / 100 != 2) {
        throw ApiException.badRequest("Jira search failed (HTTP " + status + ")");
      }
      return mapper.readTree(response.body());
    } catch (ApiException error) {
      throw error;
    } catch (Exception error) {
      throw ApiException.badRequest("Jira search failed: " + safeMessage(error));
    }
  }

  private JiraIssue parseIssue(String baseUrl, JsonNode node) {
    String key = text(node, "key");
    if (key == null) {
      return null;
    }
    JsonNode fields = node.path("fields");
    String projectKey = fields.path("project").path("key").asText("");
    String summary = fields.path("summary").asText("");
    String description = fields.path("description").isValueNode()
        ? fields.path("description").asText("") : "";
    StringBuilder components = new StringBuilder();
    JsonNode componentArray = fields.path("components");
    if (componentArray.isArray()) {
      for (JsonNode component : componentArray) {
        String name = component.path("name").asText("");
        if (!name.isBlank()) {
          if (components.length() > 0) {
            components.append(", ");
          }
          components.append(name);
        }
      }
    }
    return new JiraIssue(
        key,
        summary,
        description,
        fields.path("status").path("name").asText(""),
        fields.path("issuetype").path("name").asText(""),
        fields.path("priority").path("name").asText(""),
        fields.path("created").asText(""),
        fields.path("updated").asText(""),
        fields.path("assignee").path("displayName").asText(""),
        fields.path("reporter").path("displayName").asText(""),
        components.toString(),
        projectKey.isBlank() ? key.split("-", 2)[0] : projectKey,
        baseUrl + "/browse/" + key);
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() || value.asText("").isBlank() ? null
        : value.asText();
  }

  private static String requireHttpBase(String value) {
    if (value == null || value.isBlank()) {
      throw ApiException.badRequest("Jira base URL is not configured");
    }
    try {
      URI uri = URI.create(value.trim());
      if (uri.getScheme() == null || uri.getHost() == null
          || (!"http".equalsIgnoreCase(uri.getScheme())
              && !"https".equalsIgnoreCase(uri.getScheme()))) {
        throw ApiException.badRequest("Jira base URL must be http(s)");
      }
      String path = uri.getPath() == null ? "" : uri.getPath();
      return uri.getScheme() + "://" + uri.getHost()
          + (uri.getPort() < 0 ? "" : ":" + uri.getPort()) + path.replaceAll("/+$", "");
    } catch (IllegalArgumentException error) {
      throw ApiException.badRequest("invalid Jira base URL");
    }
  }

  private static String safeMessage(Exception error) {
    String message = error.getMessage();
    return message == null ? error.getClass().getSimpleName()
        : message.replaceAll("(?i)(bearer|token|authorization)[^,; ]*", "$1=***");
  }
}
