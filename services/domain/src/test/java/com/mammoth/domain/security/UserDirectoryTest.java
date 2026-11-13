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

/** Minimal member-selection directory: active users only; admin list still sees disabled users. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserDirectoryTest {

  private static final Path DATA_ROOT = createDataRoot();

  private static Path createDataRoot() {
    try {
      return Files.createTempDirectory("mammoth-user-directory-it");
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

  private AppUser admin;

  @BeforeAll
  void bootstrap() {
    admin = userService.create("dir-admin", "Directory Admin", "Passw0rd!2026");
  }

  @Test
  void directoryContainsActiveUsersOnlyWhileAdminListSeesDisabledUsers() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    AppUser active = userService.create("dir-active-" + suffix, "Active", "Passw0rd!2026");
    AppUser disabled = userService.create("dir-disabled-" + suffix, "Disabled", "Passw0rd!2026");
    userService.disable(admin, disabled.getId());

    assertThat(userService.directory()).extracting(AppUser::getId)
        .contains(active.getId())
        .doesNotContain(disabled.getId());
    assertThat(userService.listAsAdmin(admin)).extracting(AppUser::getId)
        .contains(active.getId(), disabled.getId());
  }
}
