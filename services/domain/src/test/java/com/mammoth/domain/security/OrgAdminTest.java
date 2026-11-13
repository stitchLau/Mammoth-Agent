package com.mammoth.domain.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.RoleAssignmentRepository;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.service.CatalogService;
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

/** ADR-0006 M4 addendum: org_admin bootstrap, authorization and last-admin protection. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrgAdminTest {

  private static final Path DATA_ROOT = createDataRoot();

  private static Path createDataRoot() {
    try {
      return Files.createTempDirectory("mammoth-org-admin-it");
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
  private CatalogService catalogService;

  @Autowired
  private RoleAssignmentRepository roleAssignmentRepository;

  private AppUser admin;

  @BeforeAll
  void bootstrap() {
    admin = userService.create(unique("admin"), "Org Admin", "Passw0rd!2026");
  }

  private static String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private AppUser user(String prefix) {
    return userService.create(unique(prefix), "User", "Passw0rd!2026");
  }

  @Test
  void firstUserBecomesOrgAdminAndOthersDoNot() {
    assertThat(authorizationService.isOrgAdmin(admin)).isTrue();
    assertThat(roleAssignmentRepository.findByRoleAndScopeTypeAndScopeId(
        AuthorizationService.ORG_ADMIN, AuthorizationService.SCOPE_ORGANIZATION,
        admin.getOrgId())).hasSize(1);

    AppUser second = user("second");
    assertThat(authorizationService.isOrgAdmin(second)).isFalse();
  }

  @Test
  void nonAdminCannotManageUsersOrMasterData() {
    AppUser other = user("other");

    assertThatThrownBy(() -> userService.listAsAdmin(other))
        .isInstanceOf(ApiException.class)
        .extracting(error -> ((ApiException) error).getStatus())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThatThrownBy(() -> userService.createAsAdmin(other, unique("forbidden"), "Forbidden",
        "Passw0rd!2026", null, AppUser.ACCOUNT_HUMAN))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("organization admin");
    assertThatThrownBy(() -> userService.disable(other, admin.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("organization admin");
    assertThatThrownBy(() -> catalogService.createCustomerAsAdmin(other, unique("ACME")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("organization admin");
  }

  @Test
  void orgAdminCanCreateListAndDirectoryUsers() {
    String username = unique("created");
    AppUser created = userService.createAsAdmin(admin, username, "Created User",
        "Passw0rd!2026", "created@example.com", AppUser.ACCOUNT_HUMAN);

    assertThat(created.getEmail()).isEqualTo("created@example.com");
    assertThat(userService.listAsAdmin(admin)).extracting(AppUser::getId).contains(created.getId());
    assertThat(userService.directory()).extracting(AppUser::getId).contains(created.getId());
  }

  @Test
  void lastActiveOrgAdminCannotBeDemoted() {
    assertThatThrownBy(() -> userService.setOrgAdmin(admin, admin.getId(), false))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("last active organization admin");
  }
}
