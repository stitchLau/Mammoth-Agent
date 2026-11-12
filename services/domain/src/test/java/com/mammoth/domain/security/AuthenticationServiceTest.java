package com.mammoth.domain.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.service.UserService;
import com.mammoth.domain.web.ApiException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** ADR-0006 authentication acceptance: hashing at rest, opaque sessions, revocation and throttling. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AuthenticationServiceTest {

  private static final Path DATA_ROOT = createDataRoot();

  private static Path createDataRoot() {
    try {
      return Files.createTempDirectory("mammoth-auth-it");
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

  private AppUser user() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    return userService.create("auth-" + suffix, "Auth User", "Passw0rd!2026");
  }

  @Test
  void loginCreatesValidSessionAndLogoutRevokesIt() {
    AppUser user = user();

    String token = authenticationService.login(user.getUsername(), "Passw0rd!2026");
    assertThat(token).isNotBlank();
    assertThat(sessionService.validate(token)).contains(user.getId());

    sessionService.revoke(token);
    assertThat(sessionService.validate(token)).isEmpty();
  }

  @Test
  void wrongPasswordIsRejectedWithoutCreatingSession() {
    AppUser user = user();

    assertThatThrownBy(() -> authenticationService.login(user.getUsername(), "wrong-password"))
        .isInstanceOf(ApiException.class)
        .extracting(error -> ((ApiException) error).getStatus())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void repeatedFailuresAreThrottled() {
    AppUser user = user();
    for (int attempt = 0; attempt < 5; attempt++) {
      assertThatThrownBy(() -> authenticationService.login(user.getUsername(), "wrong"))
          .isInstanceOf(ApiException.class);
    }

    assertThatThrownBy(() -> authenticationService.login(user.getUsername(), "Passw0rd!2026"))
        .isInstanceOf(ApiException.class)
        .extracting(error -> ((ApiException) error).getStatus())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }
}
