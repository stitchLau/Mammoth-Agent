package com.mammoth.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.AuditLogRepository;
import com.mammoth.domain.persistence.Customer;
import com.mammoth.domain.persistence.Project;
import com.mammoth.domain.persistence.ProjectMemberRepository;
import com.mammoth.domain.web.ApiException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Member role management (UI-SPEC P08): owner-only changes, last-owner protection, audit trail. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MemberManagementTest {

  private static final Path DATA_ROOT = createDataRoot();

  private static Path createDataRoot() {
    try {
      return Files.createTempDirectory("mammoth-member-it");
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
  private ProjectMemberRepository projectMemberRepository;

  @Autowired
  private AuditLogRepository auditLogRepository;

  private String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private AppUser user(String prefix) {
    return userService.create(unique(prefix), "User", "Passw0rd!2026");
  }

  private Project project(AppUser owner) {
    Customer customer = catalogService.createCustomer(owner, unique("ACME"));
    return projectService.create(owner, customer.getId(), unique("PRJ"), "member project");
  }

  @Test
  void ownerCanChangeRolesAndRemoveMembersButCannotRemoveLastOwner() {
    AppUser owner = user("owner");
    AppUser editor = user("editor");
    Project project = project(owner);
    projectService.addMember(owner, project.getId(), editor.getId(), AuthorizationService.EDITOR);

    var changed = projectService.changeMemberRole(
        owner, project.getId(), editor.getId(), AuthorizationService.REVIEWER);
    assertThat(changed.getRole()).isEqualTo(AuthorizationService.REVIEWER);

    assertThatThrownBy(() -> projectService.changeMemberRole(
        owner, project.getId(), owner.getId(), AuthorizationService.VIEWER))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("last project owner");
    assertThatThrownBy(() -> projectService.removeMember(owner, project.getId(), owner.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("last project owner");
    assertThatThrownBy(() -> projectService.changeMemberRole(
        owner, project.getId(), editor.getId(), "superuser"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("unsupported role");

    projectService.removeMember(owner, project.getId(), editor.getId());
    assertThat(projectMemberRepository.findByProjectIdAndUserId(project.getId(), editor.getId())).isEmpty();

    assertThat(auditLogRepository.findByObjectTypeAndObjectIdOrderByAtAsc("project", project.getId()))
        .extracting(log -> log.getAction())
        .contains("project.member.add", "project.member.role_change", "project.member.remove");
  }

  @Test
  void nonOwnerCannotManageMembers() {
    AppUser owner = user("owner2");
    AppUser editor = user("editor2");
    Project project = project(owner);
    projectService.addMember(owner, project.getId(), editor.getId(), AuthorizationService.EDITOR);

    assertThatThrownBy(() -> projectService.changeMemberRole(
        editor, project.getId(), editor.getId(), AuthorizationService.VIEWER))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("only the project owner");
    assertThatThrownBy(() -> projectService.removeMember(editor, project.getId(), editor.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("only the project owner");
  }
}
