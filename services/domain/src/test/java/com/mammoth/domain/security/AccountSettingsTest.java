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

/** UI-SPEC P09: profile update and password rotation (all sessions revoked). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AccountSettingsTest {

  private static final Path DATA_ROOT = createDataRoot();

  private static Path createDataRoot() {
    try {
      return Files.createTempDirectory("mammoth-account-it");
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

  private AppUser user() {
    return userService.create("acct-" + UUID.randomUUID().toString().substring(0, 8), "Account User",
        "Passw0rd!2026");
  }

  @Test
  void displayNameCanBeUpdated() {
    AppUser user = user();

    AppUser updated = accountService.updateDisplayName(user.getId(), "新的显示名");

    assertThat(updated.getDisplayName()).isEqualTo("新的显示名");
    assertThatThrownBy(() -> accountService.updateDisplayName(user.getId(), "  "))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("displayName");
  }

  @Test
  void emailCanBeSetClearedAndValidated() {
    AppUser user = user();

    AppUser withEmail = accountService.updateProfile(user.getId(), user.getDisplayName(),
        "user@example.com");
    assertThat(withEmail.getEmail()).isEqualTo("user@example.com");

    AppUser cleared = accountService.updateProfile(user.getId(), user.getDisplayName(), "");
    assertThat(cleared.getEmail()).isNull();

    assertThatThrownBy(() -> accountService.updateProfile(
        user.getId(), user.getDisplayName(), "not-an-email"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("email is not valid");
  }

  @Test
  void passwordChangeRequiresCurrentPasswordAndRevokesSessions() {
    AppUser user = user();
    String token = authenticationService.login(user.getUsername(), "Passw0rd!2026");
    assertThat(sessionService.validate(token)).contains(user.getId());

    assertThatThrownBy(() -> accountService.changePassword(user.getId(), "wrong-current", "NewPassw0rd!2026"))
        .isInstanceOf(ApiException.class)
        .extracting(error -> ((ApiException) error).getStatus())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThatThrownBy(() -> accountService.changePassword(user.getId(), "Passw0rd!2026", "short"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("at least 8 characters");

    accountService.changePassword(user.getId(), "Passw0rd!2026", "NewPassw0rd!2026");

    assertThat(sessionService.validate(token)).isEmpty();
    assertThat(authenticationService.login(user.getUsername(), "NewPassw0rd!2026")).isNotBlank();
  }
}
