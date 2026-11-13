package com.mammoth.domain.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mammoth.domain.config.JiraProperties;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.Customer;
import com.mammoth.domain.persistence.Project;
import com.mammoth.domain.service.CatalogService;
import com.mammoth.domain.service.ProjectService;
import com.mammoth.domain.service.UserService;
import com.mammoth.domain.web.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** M4.5-1: Jira Data Center v2 read-only contract test with a local stub (no real Jira). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JiraImportTest {

  private static final Path DATA_ROOT = createDataRoot();
  private static final AtomicBoolean AUTH_OK = new AtomicBoolean(false);
  private static final AtomicInteger REQUESTS = new AtomicInteger();
  private static final HttpServer JIRA = startJira();

  private static Path createDataRoot() {
    try {
      return Files.createTempDirectory("mammoth-jira-it");
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  private static HttpServer startJira() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/rest/api/2/search", exchange -> {
        REQUESTS.incrementAndGet();
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (!"Bearer test-token".equals(auth)) {
          exchange.sendResponseHeaders(401, -1);
          exchange.close();
          return;
        }
        AUTH_OK.set(true);
        String query = exchange.getRequestURI().getQuery();
        int startAt = query != null && query.contains("startAt=1") ? 1 : 0;
        String issue = startAt == 0
            ? "{\"key\":\"DWMS-1\",\"fields\":{\"summary\":\"Jira 需求一\","
                + "\"description\":\"只读导入描述\",\"status\":{\"name\":\"Open\"},"
                + "\"issuetype\":{\"name\":\"Bug\"},\"priority\":{\"name\":\"High\"},"
                + "\"created\":\"2026-01-01T00:00:00.000+0000\","
                + "\"updated\":\"2026-01-02T00:00:00.000+0000\","
                + "\"assignee\":{\"displayName\":\"Alice\"},"
                + "\"reporter\":{\"displayName\":\"Bob\"},"
                + "\"components\":[{\"name\":\"Core\"}],\"project\":{\"key\":\"DWMS\"}}}"
            : "{\"key\":\"DWMS-2\",\"fields\":{\"summary\":\"Jira 需求二\","
                + "\"description\":\"第二页\",\"status\":{\"name\":\"Open\"},"
                + "\"issuetype\":{\"name\":\"Task\"},\"priority\":{\"name\":\"Medium\"},"
                + "\"created\":\"2026-01-03T00:00:00.000+0000\","
                + "\"updated\":\"2026-01-04T00:00:00.000+0000\","
                + "\"assignee\":{\"displayName\":\"Alice\"},"
                + "\"reporter\":{\"displayName\":\"Bob\"},"
                + "\"components\":[],\"project\":{\"key\":\"DWMS\"}}}";
        String body = "{\"total\":2,\"startAt\":" + startAt
            + ",\"maxResults\":1,\"issues\":[" + issue + "]}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
      });
      server.start();
      return server;
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("mammoth.data-root", DATA_ROOT::toString);
    registry.add("mammoth.jira.base-url", () -> "http://127.0.0.1:"
        + JIRA.getAddress().getPort());
    registry.add("mammoth.jira.token", () -> "test-token");
    registry.add("mammoth.jira.page-size", () -> "1");
    registry.add("mammoth.jira.max-results", () -> "10");
  }

  @AfterAll
  static void stopJira() {
    JIRA.stop(0);
  }

  @Autowired
  private UserService userService;

  @Autowired
  private CatalogService catalogService;

  @Autowired
  private ProjectService projectService;

  @Autowired
  private ExternalImportService importService;

  @Autowired
  private ExternalRecordRepository recordRepository;

  @Autowired
  private JiraImportService jiraImportService;

  @Autowired
  private JiraProperties jiraProperties;

  private AppUser admin;
  private Project project;
  private ExternalSource source;

  @BeforeAll
  void bootstrap() {
    admin = userService.create("jira-admin", "Jira Admin", "Passw0rd!2026");
    Customer customer = catalogService.createCustomer(admin, "JIRA-ACME");
    project = projectService.create(admin, customer.getId(), "JIRA-1", "Jira project");
    source = importService.createSource(admin, project.getId(),
        new ExternalImportService.SourceInput("jira", "Jira API", null));
  }

  @Test
  void previewAndApplyPaginateAndNeverWriteToJira() {
    ImportReport.Preview preview = jiraImportService.preview(admin, project.getId(),
        source.getId(), "project = DWMS ORDER BY updated DESC", 10);
    assertThat(preview.acceptedCount()).isEqualTo(2);
    assertThat(preview.rejectedCount()).isZero();
    assertThat(AUTH_OK.get()).isTrue();
    assertThat(REQUESTS.get()).isGreaterThanOrEqualTo(2);

    ExternalImportService.ImportResult result = jiraImportService.apply(admin, project.getId(),
        source.getId(), "project = DWMS ORDER BY updated DESC", 10);
    assertThat(result.duplicate()).isFalse();
    assertThat(result.accepted()).isEqualTo(2);
    assertThat(recordRepository.findByProjectIdOrderByCreatedAtAsc(project.getId()))
        .anyMatch(record -> "DWMS-1".equals(record.getExternalKey())
            && record.getMetadataJson().contains("\"jiraProjectKey\":\"DWMS\"")
            && record.getSourceUrlSanitized().contains("/browse/DWMS-1"));
  }

  @Test
  void missingTokenIsRejectedWithoutNetworkCall() {
    JiraClient client = new JiraClient();
    assertThatThrownBy(() -> client.search(jiraProperties.getBaseUrl(), "", "project = DWMS", 1, 1))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("token");
  }
}
