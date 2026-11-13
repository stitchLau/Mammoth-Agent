package com.mammoth.domain.service;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.AppUserRepository;
import com.mammoth.domain.persistence.ProjectMember;
import com.mammoth.domain.persistence.ProjectMemberRepository;
import com.mammoth.domain.persistence.RoleAssignmentRepository;
import com.mammoth.domain.web.ApiException;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deny-by-default authorization (ADR-0006).
 *
 * <p>Project actions are authorized against {@code project_member}; organization administration is
 * authorized against {@code role_assignment} ({@code org_admin}). The two scopes are deliberately
 * separate: an org admin does not automatically gain project write access.
 */
@Service
public class AuthorizationService {

  public static final String OWNER = "owner";
  public static final String EDITOR = "editor";
  public static final String REVIEWER = "reviewer";
  public static final String VIEWER = "viewer";
  public static final String ORG_ADMIN = "org_admin";
  public static final String SCOPE_ORGANIZATION = "organization";

  private final AppUserRepository appUserRepository;
  private final ProjectMemberRepository projectMemberRepository;
  private final RoleAssignmentRepository roleAssignmentRepository;
  private final OrganizationService organizationService;

  public AuthorizationService(
      AppUserRepository appUserRepository,
      ProjectMemberRepository projectMemberRepository,
      RoleAssignmentRepository roleAssignmentRepository,
      OrganizationService organizationService) {
    this.appUserRepository = appUserRepository;
    this.projectMemberRepository = projectMemberRepository;
    this.roleAssignmentRepository = roleAssignmentRepository;
    this.organizationService = organizationService;
  }

  /** Resolve an active user or fail with 401. */
  @Transactional(readOnly = true)
  public AppUser requireActiveUser(String userId) {
    if (userId == null || userId.isBlank()) {
      throw ApiException.unauthorized("missing user identity");
    }
    return appUserRepository.findById(userId)
        .filter(AppUser::isActive)
        .orElseThrow(() -> ApiException.unauthorized("unknown or inactive user: " + userId));
  }

  /** Require one of the allowed roles on a project, or fail with 403. */
  @Transactional(readOnly = true)
  public ProjectMember requireProjectRole(AppUser user, String projectId, Set<String> allowedRoles) {
    ProjectMember membership = projectMemberRepository
        .findByProjectIdAndUserId(projectId, user.getId())
        .orElseThrow(() -> ApiException.forbidden("no access to project " + projectId));
    if (!allowedRoles.contains(membership.getRole())) {
      throw ApiException.forbidden("role " + membership.getRole() + " cannot perform this action");
    }
    return membership;
  }

  /** Organization-level administration role (ADR-0006 M4 addendum). */
  @Transactional(readOnly = true)
  public boolean isOrgAdmin(AppUser user) {
    if (user == null) {
      return false;
    }
    return roleAssignmentRepository.existsByUserIdAndRoleAndScopeTypeAndScopeId(
        user.getId(), ORG_ADMIN, SCOPE_ORGANIZATION, organizationService.currentOrgId());
  }

  @Transactional(readOnly = true)
  public void requireOrgAdmin(AppUser user) {
    if (!isOrgAdmin(user)) {
      throw ApiException.forbidden("organization admin role required");
    }
  }

  @Transactional(readOnly = true)
  public List<ProjectMember> membershipsOf(AppUser user) {
    return projectMemberRepository.findByUserId(user.getId());
  }
}
