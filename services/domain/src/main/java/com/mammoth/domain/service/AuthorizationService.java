package com.mammoth.domain.service;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.AppUserRepository;
import com.mammoth.domain.persistence.ProjectMember;
import com.mammoth.domain.persistence.ProjectMemberRepository;
import com.mammoth.domain.web.ApiException;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deny-by-default authorization (ADR-0006).
 *
 * <p>M1 skeleton: the caller identity arrives as the {@code X-Mammoth-User-Id} header. Session
 * cookies + Spring Security replace this header in the authentication task; every authorization
 * decision already goes through this single service so only the identity source changes.
 */
@Service
public class AuthorizationService {

  public static final String OWNER = "owner";
  public static final String EDITOR = "editor";
  public static final String REVIEWER = "reviewer";
  public static final String VIEWER = "viewer";

  private final AppUserRepository appUserRepository;
  private final ProjectMemberRepository projectMemberRepository;

  public AuthorizationService(
      AppUserRepository appUserRepository,
      ProjectMemberRepository projectMemberRepository) {
    this.appUserRepository = appUserRepository;
    this.projectMemberRepository = projectMemberRepository;
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

  @Transactional(readOnly = true)
  public List<ProjectMember> membershipsOf(AppUser user) {
    return projectMemberRepository.findByUserId(user.getId());
  }
}
