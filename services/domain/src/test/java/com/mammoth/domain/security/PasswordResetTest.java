package com.mammoth.domain.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.service.UserService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** ADR-0006 M4 addendum: admin reset issues a one-time password and forces rotation. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PasswordResetTest {

  private static final Path DATA_ROOT = createDataRoot();

  private static Path createDataRoot() {
    try {
      return Files.createTempDirectory("mammoth-password-reset-it");
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
  private AuthenticationService authenticationService;

  @Autowired
  private SessionService sessionService;

  @Autowired
  private AccountService accountService;

  private AppUser admin;

  @BeforeAll
  void bootstrap() {
    admin = userService.create("reset-admin", "Reset Admin", "Passw0rd!2026");
  }

  @Test
  void resetIssuesTemporaryPasswordAndForcesChange() {
    String username = "reset-" + UUID.randomUUID().toString().substring(0, 8);
    AppUser user = userService.create(username, "Reset User", "Passw0rd!2026");
    String oldToken = authenticationService.login(username, "Passw0rd!2026");

    String temporaryPassword = userService.resetPassword(admin, user.getId());

    assertThat(temporaryPassword).hasSizeGreaterThanOrEqualTo(8);
    assertThat(sessionService.validate(oldToken)).isEmpty();

    String temporaryToken = authenticationService.login(username, temporaryPassword);
    assertThat(sessionService.validateSession(temporaryToken))
        .isPresent()
        .get()
        .satisfies(session -> assertThat(session.passwordResetRequired()).isTrue());

    String newPassword = "NewPassw0rd!2026";
    accountService.changePassword(user.getId(), temporaryPassword, newPassword);

    assertThat(sessionService.validate(temporaryToken)).isEmpty();
    String fullToken = authenticationService.login(username, newPassword);
    assertThat(sessionService.validateSession(fullToken))
        .isPresent()
        .get()
        .satisfies(session -> assertThat(session.passwordResetRequired()).isFalse());
  }
}
