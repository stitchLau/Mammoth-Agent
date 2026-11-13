package com.mammoth.domain.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.service.UserService;
import com.mammoth.domain.web.ApiException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** ADR-0006 M4 addendum: disable/enable, session revocation, agent accounts and org roles. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserLifecycleTest {

  private static final Path DATA_ROOT = createDataRoot();

  private static Path createDataRoot() {
    try {
      return Files.createTempDirectory("mammoth-user-lifecycle-it");
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("mammoth.data-root", DATA_ROOT::toString);
  }

  @Autowired
  private UserService userService;

  @Autowired
  private AuthorizationService authorizationService;

  @Autowired
  private AuthenticationService authenticationService;

  @Autowired
  private SessionService sessionService;

  private AppUser admin;

  @BeforeAll
  void bootstrap() {
    admin = userService.create(unique("admin"), "Lifecycle Admin", "Passw0rd!2026");
  }

  private static String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private AppUser user(String prefix) {
    return userService.create(unique(prefix), "User", "Passw0rd!2026");
  }

  @Test
  void disableRevokesSessionsAndBlocksLoginUntilEnabled() {
    AppUser user = user("disable");
    String token = authenticationService.login(user.getUsername(), "Passw0rd!2026");
    assertThat(sessionService.validate(token)).contains(user.getId());

    userService.disable(admin, user.getId());

    assertThat(sessionService.validate(token)).isEmpty();
    assertThatThrownBy(() -> authenticationService.login(user.getUsername(), "Passw0rd!2026"))
        .isInstanceOf(ApiException.class)
        .extracting(error -> ((ApiException) error).getStatus())
        .isEqualTo(HttpStatus.UNAUTHORIZED);

    userService.enable(admin, user.getId());
    assertThat(authenticationService.login(user.getUsername(), "Passw0rd!2026")).isNotBlank();
  }

  @Test
  void agentAccountCannotLogInOrResetPassword() {
    String username = unique("agent");
    AppUser agent = userService.createAsAdmin(admin, username, "Agent", "Passw0rd!2026",
        null, AppUser.ACCOUNT_AGENT);

    assertThat(agent.isAgent()).isTrue();
    assertThatThrownBy(() -> authenticationService.login(username, "Passw0rd!2026"))
        .isInstanceOf(ApiException.class)
        .extracting(error -> ((ApiException) error).getStatus())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThatThrownBy(() -> userService.resetPassword(admin, agent.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("agent accounts cannot log in");
  }

  @Test
  void orgAdminCanBeGrantedAndRevoked() {
    AppUser user = user("org-role");
    userService.setOrgAdmin(admin, user.getId(), true);
    assertThat(authorizationService.isOrgAdmin(user)).isTrue();

    userService.setOrgAdmin(admin, user.getId(), false);
    assertThat(authorizationService.isOrgAdmin(user)).isFalse();
  }

  @Test
  void selfDisableIsRejected() {
    assertThatThrownBy(() -> userService.disable(admin, admin.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cannot disable your own account");
  }
}
