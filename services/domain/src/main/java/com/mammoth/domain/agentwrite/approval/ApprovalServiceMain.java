package com.mammoth.domain.agentwrite.approval;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * ADR-0015 P2: the isolated approval-service process that applies human-approved proposals.
 *
 * <p>Runs under a dedicated OS account (see
 * {@code scripts/windows/approval-service-provision.ps1}) and reads the apply token from an
 * OS-protected token file (service-account-only ACL; P2). The agent/Host process never receives
 * this token, so it cannot apply proposals directly. Two modes:
 *
 * <ul>
 *   <li>{@code --proposal-id <id>}: apply one already-approved proposal;</li>
 *   <li>{@code --watch --project-id <id> [--once] [--poll-seconds 5]}: poll the Domain
 *       work-queue endpoint and apply every approved proposal.</li>
 * </ul>
 *
 * <p>Common options: {@code --domain-base http://127.0.0.1:8080}, {@code --token-file <path>},
 * {@code --timeout-seconds 30}. Missing/blank token file fails closed with exit code 2.
 */
public final class ApprovalServiceMain {

  /** Keep in sync with the Domain controller header. */
  public static final String APPLY_TOKEN_HEADER = "X-Mammoth-Apply-Token";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ApprovalServiceMain() {
  }

  public static void main(String[] args) {
    System.exit(run(args));
  }

  /** Testable entry point: returns a process exit code instead of exiting the JVM. */
  public static int run(String[] args) {
    Map<String, String> options = parse(args);
    boolean watch = options.containsKey("watch");
    String domainBase = firstNonBlank(options.get("domain-base"),
        System.getenv("MAMMOTH_DOMAIN_BASE"));
    String tokenFile = firstNonBlank(options.get("token-file"),
        System.getenv("MAMMOTH_APPROVAL_TOKEN_FILE"));
    String timeoutValue = firstNonBlank(options.get("timeout-seconds"),
        System.getenv("MAMMOTH_APPROVAL_TIMEOUT_SECONDS"), "30");
    String proposalId = options.get("proposal-id");
    String projectId = options.get("project-id");
    String pollValue = firstNonBlank(options.get("poll-seconds"), "5");
    boolean once = options.containsKey("once");

    if (domainBase == null || tokenFile == null || (watch ? projectId == null : proposalId == null)) {
      System.err.println("usage: --approval-service "
          + "(--proposal-id <id> | --watch --project-id <id> [--once] [--poll-seconds 5]) "
          + "[--domain-base http://127.0.0.1:8080] [--token-file <path>] "
          + "[--timeout-seconds 30]");
      return 2;
    }
    if (!domainBase.startsWith("http://") && !domainBase.startsWith("https://")) {
      System.err.println("approval-service: domain-base must be an http(s) URL");
      return 2;
    }
    if (!watch && !proposalId.matches("[A-Za-z0-9-]+")) {
      System.err.println("approval-service: proposal-id must be a simple id");
      return 2;
    }
    if (watch && !projectId.matches("[A-Za-z0-9-]+")) {
      System.err.println("approval-service: project-id must be a simple id");
      return 2;
    }

    String token;
    try {
      token = Files.readString(Path.of(tokenFile), StandardCharsets.UTF_8).trim();
    } catch (IOException error) {
      System.err.println("approval-service: cannot read apply token file: " + error.getMessage());
      return 2;
    }
    if (token.isEmpty()) {
      System.err.println("approval-service: apply token file is empty (fail-closed)");
      return 2;
    }

    long timeoutSeconds = parseLong(timeoutValue, 30);
    long pollSeconds = Math.max(1, parseLong(pollValue, 5));
    long timeout = Math.max(1, timeoutSeconds);
    String normalizedBase = domainBase.replaceAll("/+$", "");
    HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(timeout))
        .build();

    if (watch) {
      return watchLoop(client, normalizedBase, token, projectId, pollSeconds, timeout, once);
    }
    return applyOne(client, normalizedBase, token, proposalId, timeout) == 0 ? 0 : 1;
  }

  private static int watchLoop(HttpClient client, String domainBase, String token,
      String projectId, long pollSeconds, long timeout, boolean once) {
    String url = domainBase + "/api/agent/proposals/approved?projectId="
        + URLEncoder.encode(projectId, StandardCharsets.UTF_8);
    while (true) {
      int applied = 0;
      int failed = 0;
      try {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(timeout))
            .header(APPLY_TOKEN_HEADER, token)
            .header("accept", "application/json")
            .GET()
            .build();
        HttpResponse<String> response = client.send(request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
          System.out.println("work queue rejected: http=" + response.statusCode() + " body="
              + abbreviate(response.body()));
          if (once) {
            return 1;
          }
        } else {
          JsonNode list = tryParse(response.body());
          if (list != null && list.isArray()) {
            for (JsonNode item : list) {
              String proposalId = text(item, "id");
              if (proposalId.isEmpty()) {
                continue;
              }
              if (applyOne(client, domainBase, token, proposalId, timeout) == 0) {
                applied++;
              } else {
                failed++;
              }
            }
          }
          if (once) {
            System.out.println("work queue processed: applied=" + applied + " failed=" + failed);
            return failed == 0 ? 0 : 1;
          }
        }
      } catch (IOException | InterruptedException error) {
        if (error instanceof InterruptedException) {
          Thread.currentThread().interrupt();
          return 1;
        }
        System.out.println("work queue poll failed: " + error.getMessage());
        if (once) {
          return 1;
        }
      }
      try {
        Thread.sleep(pollSeconds * 1000L);
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        return 1;
      }
    }
  }

  private static int applyOne(HttpClient client, String domainBase, String token, String proposalId,
      long timeout) {
    String url = domainBase + "/api/agent/proposals/"
        + URLEncoder.encode(proposalId, StandardCharsets.UTF_8).replace("+", "%20") + "/apply";
    HttpRequest request = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(timeout))
        .header(APPLY_TOKEN_HEADER, token)
        .header("accept", "application/json")
        .POST(HttpRequest.BodyPublishers.noBody())
        .build();
    try {
      HttpResponse<String> response = client.send(request,
          HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      JsonNode body = tryParse(response.body());
      String status = body == null ? null : text(body, "status");
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        if ("applied".equals(status)) {
          System.out.println("applied proposal=" + proposalId + " domainObjectId="
              + text(body, "domainObjectId") + " itemNo="
              + text(body, "domainObjectItemNo"));
          return 0;
        }
        System.out.println("not applied: proposal=" + proposalId + " status=" + status
            + " reason=" + text(body, "failureReason") + " http=" + response.statusCode());
        return 1;
      }
      System.out.println("apply rejected: proposal=" + proposalId + " http="
          + response.statusCode() + " body=" + abbreviate(response.body()));
      return 1;
    } catch (IOException | InterruptedException error) {
      if (error instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      System.out.println("apply failed: proposal=" + proposalId + " " + error.getMessage());
      return 1;
    }
  }

  private static Map<String, String> parse(String[] args) {
    Map<String, String> options = new HashMap<>();
    for (int index = 0; index < args.length; index++) {
      String arg = args[index];
      if (!arg.startsWith("--")) {
        continue;
      }
      String key = arg.substring(2).toLowerCase(Locale.ROOT);
      if (index + 1 < args.length && !args[index + 1].startsWith("--")) {
        options.put(key, args[++index]);
      } else {
        options.put(key, "true");
      }
    }
    return options;
  }

  private static long parseLong(String value, long fallback) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException error) {
      return fallback;
    }
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value.trim();
      }
    }
    return null;
  }

  private static JsonNode tryParse(String json) {
    try {
      return MAPPER.readTree(json);
    } catch (RuntimeException error) {
      return null;
    }
  }

  private static String text(JsonNode body, String field) {
    if (body == null) {
      return "";
    }
    JsonNode node = body.get(field);
    return node == null || node.isNull() ? "" : node.asText();
  }

  private static String abbreviate(String value) {
    if (value == null) {
      return "";
    }
    String oneLine = value.replaceAll("\\s+", " ").trim();
    return oneLine.length() <= 300 ? oneLine : oneLine.substring(0, 300) + "...";
  }
}
