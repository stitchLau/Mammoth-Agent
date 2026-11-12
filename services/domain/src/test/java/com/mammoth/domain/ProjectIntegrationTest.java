package com.mammoth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.AppUserRepository;
import com.mammoth.domain.persistence.AuditLogRepository;
import com.mammoth.domain.persistence.Customer;
import com.mammoth.domain.persistence.Project;
import com.mammoth.domain.persistence.ProjectMember;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.service.CatalogService;
import com.mammoth.domain.service.ProjectService;
import com.mammoth.domain.service.UserService;
import com.mammoth.domain.web.ApiException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * T4 acceptance: master data + project + ownership/membership + authorization + audit, exercised
 * against the real SQLite database created by Flyway in a temporary data root.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectIntegrationTest {

  private static final Path DATA_ROOT = createDataRoot();

  private static Path createDataRoot() {
    try {
      return Files.createTempDirectory("mammoth-t4-it");
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
  private CatalogService catalogService;

  @Autowired
  private ProjectService projectService;

  @Autowired
  private AuthorizationService authorizationService;

  @Autowired
  private AppUserRepository appUserRepository;

  @Autowired
  private AuditLogRepository auditLogRepository;

  private String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  @Test
  void createsProjectBoundToCustomerWithOwnerMembershipAndAudit() {
    AppUser owner = userService.create(unique("owner"), "Owner", "Passw0rd!2026");
    Customer customer = catalogService.createCustomer(owner, unique("ACME"));
    Project project = projectService.create(owner, customer.getId(), unique("PRJ"), "MES 项目");

    assertThat(project.getCustomerId()).isEqualTo(customer.getId());
    assertThat(projectService.members(project.getId()))
        .extracting(ProjectMember::getRole)
        .containsExactly(AuthorizationService.OWNER);

    assertThat(auditLogRepository.countByAction("project.create")).isPositive();
    assertThat(auditLogRepository.findByObjectTypeAndObjectIdOrderByAtAsc("project", project.getId()))
        .isNotEmpty();
    assertThat(projectService.listVisible(owner))
        .extracting(Project::getId)
        .contains(project.getId());
  }

  @Test
  void rejectsDuplicateProjectCodeAndUnknownCustomer() {
    AppUser owner = userService.create(unique("owner2"), "Owner2", "Passw0rd!2026");
    Customer customer = catalogService.createCustomer(owner, unique("ACME2"));
    String code = unique("DUP");
    projectService.create(owner, customer.getId(), code, "first");

    assertThatThrownBy(() -> projectService.create(owner, customer.getId(), code, "second"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("already exists");

    assertThatThrownBy(() -> projectService.create(owner, "missing-customer", unique("PRJ2"), "x"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("customer not found");
  }

  @Test
  void deniesNonMembersAndProtectsMembershipManagement() {
    AppUser owner = userService.create(unique("owner3"), "Owner3", "Passw0rd!2026");
    AppUser outsider = userService.create(unique("outsider"), "Outsider", "Passw0rd!2026");
    Customer customer = catalogService.createCustomer(owner, unique("ACME3"));
    Project project = projectService.create(owner, customer.getId(), unique("PRJ3"), "secured");

    assertThatThrownBy(() -> authorizationService.requireProjectRole(
        outsider, project.getId(), Set.of(AuthorizationService.VIEWER)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("no access to project");

    // An editor may read but must not manage members (owner-only).
    ProjectMember editor = projectService.addMember(
        owner, project.getId(), outsider.getId(), AuthorizationService.EDITOR);
    assertThat(editor.getRole()).isEqualTo(AuthorizationService.EDITOR);

    assertThatThrownBy(() -> authorizationService.requireProjectRole(
        outsider, project.getId(), Set.of(AuthorizationService.OWNER)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cannot perform this action");

    assertThatThrownBy(() -> projectService.addMember(
        outsider, project.getId(), owner.getId(), AuthorizationService.VIEWER))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("already a member");
  }

  @Test
  void userCreationHashesPasswordAndValidatesInput() {
    String password = "Passw0rd!2026";
    AppUser user = userService.create(unique("hash"), "Hash User", password);
    AppUser stored = appUserRepository.findById(user.getId()).orElseThrow();

    assertThat(stored.getPasswordHash()).startsWith("$2");
    assertThat(stored.getPasswordHash()).doesNotContain(password);

    assertThatThrownBy(() -> userService.create(unique("short"), "Short", "short"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("password must contain at least 8 characters");
  }
}
