package com.mammoth.domain.agentwrite.approval;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ADR-0015 P2: the approval-service CLI is fail-closed and uses the dedicated apply token. */
class ApprovalServiceMainTest {

  @TempDir
  Path tempDir;

  private HttpServer server;
  private final AtomicReference<String> tokenHeader = new AtomicReference<>();
  private final AtomicReference<String> requestPath = new AtomicReference<>();

  private int startServer(int status, String body) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      tokenHeader.set(exchange.getRequestHeaders()
          .getFirst(ApprovalServiceMain.APPLY_TOKEN_HEADER));
      requestPath.set(exchange.getRequestURI().getPath());
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("content-type", "application/json");
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    server.start();
    return server.getAddress().getPort();
  }

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  private Path tokenFile(String value) throws Exception {
    Path file = tempDir.resolve("apply-token.txt");
    Files.writeString(file, value, StandardCharsets.UTF_8);
    return file;
  }

  @Test
  void appliesUsingTheTokenFileAndDedicatedApplyHeader() throws Exception {
    int port = startServer(200, "{\"proposalId\":\"p-1\",\"status\":\"applied\","
        + "\"domainObjectId\":\"w-1\",\"domainObjectItemNo\":\"REQ-0001\"}");
    int exit = ApprovalServiceMain.run(new String[] {
        "--domain-base", "http://127.0.0.1:" + port,
        "--proposal-id", "p-1",
        "--token-file", tokenFile("apply-secret").toString()});

    assertThat(exit).isZero();
    assertThat(tokenHeader.get()).isEqualTo("apply-secret");
    assertThat(requestPath.get()).isEqualTo("/api/agent/proposals/p-1/apply");
  }

  @Test
  void failsClosedWhenTokenFileIsMissing() {
    int exit = ApprovalServiceMain.run(new String[] {
        "--domain-base", "http://127.0.0.1:1",
        "--proposal-id", "p-1",
        "--token-file", tempDir.resolve("missing.txt").toString()});
    assertThat(exit).isEqualTo(2);
  }

  @Test
  void failsClosedWhenTokenFileIsEmpty() throws Exception {
    int exit = ApprovalServiceMain.run(new String[] {
        "--domain-base", "http://127.0.0.1:1",
        "--proposal-id", "p-1",
        "--token-file", tokenFile("  \n").toString()});
    assertThat(exit).isEqualTo(2);
  }

  @Test
  void reportsRejectionWhenDomainRejectsTheApplyToken() throws Exception {
    int port = startServer(401, "{\"detail\":\"invalid approval service token\"}");
    int exit = ApprovalServiceMain.run(new String[] {
        "--domain-base", "http://127.0.0.1:" + port,
        "--proposal-id", "p-1",
        "--token-file", tokenFile("wrong").toString()});
    assertThat(exit).isEqualTo(1);
  }

  @Test
  void failedApplyResponseIsNonZero() throws Exception {
    int port = startServer(200, "{\"status\":\"failed\","
        + "\"failureReason\":\"stored payload is missing title\"}");
    int exit = ApprovalServiceMain.run(new String[] {
        "--domain-base", "http://127.0.0.1:" + port,
        "--proposal-id", "p-1",
        "--token-file", tokenFile("apply-secret").toString()});
    assertThat(exit).isEqualTo(1);
  }

  @Test
  void rejectsMalformedArguments() {
    assertThat(ApprovalServiceMain.run(new String[] {"--proposal-id", "p-1"})).isEqualTo(2);
    assertThat(ApprovalServiceMain.run(new String[] {
        "--domain-base", "ftp://example", "--proposal-id", "p-1",
        "--token-file", "x"})).isEqualTo(2);
    assertThat(ApprovalServiceMain.run(new String[] {
        "--domain-base", "http://127.0.0.1:1", "--proposal-id", "bad/id",
        "--token-file", "x"})).isEqualTo(2);
  }

  @Test
  void watchOnceAppliesEveryApprovedProposalFromTheWorkQueue() throws Exception {
    AtomicReference<String> queueToken = new AtomicReference<>();
    AtomicReference<String> queuePath = new AtomicReference<>();
    java.util.List<String> appliedIds = new java.util.concurrent.CopyOnWriteArrayList<>();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      String path = exchange.getRequestURI().getPath();
      if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
        queueToken.set(exchange.getRequestHeaders().getFirst(ApprovalServiceMain.APPLY_TOKEN_HEADER));
        queuePath.set(path);
        byte[] bytes = ("[{\"id\":\"p-1\",\"status\":\"approved\"},"
            + "{\"id\":\"p-2\",\"status\":\"approved\"}]").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("content-type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
      } else {
        appliedIds.add(path);
        byte[] bytes = "{\"status\":\"applied\",\"domainObjectId\":\"w-1\"}"
            .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("content-type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
      }
      exchange.close();
    });
    server.start();
    int port = server.getAddress().getPort();

    int exit = ApprovalServiceMain.run(new String[] {
        "--domain-base", "http://127.0.0.1:" + port,
        "--token-file", tokenFile("apply-secret").toString(),
        "--watch", "--project-id", "proj-1", "--once"});

    assertThat(exit).isZero();
    assertThat(queueToken.get()).isEqualTo("apply-secret");
    assertThat(queuePath.get()).isEqualTo("/api/agent/proposals/approved");
    assertThat(appliedIds).containsExactly(
        "/api/agent/proposals/p-1/apply", "/api/agent/proposals/p-2/apply");
  }
}
