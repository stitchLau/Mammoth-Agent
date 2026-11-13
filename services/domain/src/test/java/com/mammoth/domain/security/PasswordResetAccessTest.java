package com.mammoth.domain.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.service.UserService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * HTTP-level proof of the forced-password-change gate (ADR-0006 M4 addendum): a temporary-password
 * session can read itself but cannot reach project APIs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PasswordResetAccessTest {

  private static final Path DATA_ROOT = createDataRoot();

  private static Path createDataRoot() {
    try {
      return Files.createTempDirectory("mammoth-password-access-it");
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("mammoth.data-root", DATA_ROOT::toString);
  }

  @Value("${local.server.port}")
  private int port;

  @Autowired
  private UserService userService;

  private final HttpClient client = HttpClient.newHttpClient();

  private AppUser admin;

  @BeforeAll
  void bootstrap() {
    admin = userService.create("access-admin", "Access Admin", "Passw0rd!2026");
  }

  @Test
  void temporaryPasswordSessionIsRestrictedToPasswordChangeEndpoints() throws Exception {
    String username = "access-user";
    AppUser user = userService.create(username, "Access User", "Passw0rd!2026");
    String temporaryPassword = userService.resetPassword(admin, user.getId());

    String cookie = login(username, temporaryPassword);

    HttpResponse<String> me = get("/api/auth/me", cookie);
    assertThat(me.statusCode()).isEqualTo(200);
    assertThat(me.body()).contains("\"passwordResetRequired\":true");

    HttpResponse<String> projects = get("/api/projects", cookie);
    assertThat(projects.statusCode()).isEqualTo(403);
  }

  @Test
  void fullSessionCanReachProjectApi() throws Exception {
    String username = "access-full";
    userService.create(username, "Access Full", "Passw0rd!2026");
    String cookie = login(username, "Passw0rd!2026");

    assertThat(get("/api/projects", cookie).statusCode()).isEqualTo(200);
  }

  private String login(String username, String password) throws Exception {
    HttpRequest request = HttpRequest.newBuilder(uri("/api/auth/login"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(
            "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
        .build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    String setCookie = response.headers().firstValue("set-cookie").orElseThrow();
    String cookie = setCookie.split(";", 2)[0];
    assertThat(cookie).startsWith(SessionService.COOKIE_NAME + "=");
    return cookie;
  }

  private HttpResponse<String> get(String path, String cookie) throws Exception {
    HttpRequest request = HttpRequest.newBuilder(uri(path))
        .header("Cookie", cookie)
        .GET()
        .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private URI uri(String path) {
    return URI.create("http://127.0.0.1:" + port + path);
  }
}
