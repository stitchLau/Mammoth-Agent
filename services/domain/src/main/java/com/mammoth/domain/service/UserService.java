package com.mammoth.domain.service;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.AppUserRepository;
import com.mammoth.domain.persistence.RoleAssignment;
import com.mammoth.domain.persistence.RoleAssignmentRepository;
import com.mammoth.domain.security.SessionService;
import com.mammoth.domain.support.Ids;
import com.mammoth.domain.web.ApiException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local account provisioning and lifecycle (ADR-0006 + 2026-11-13 M4 addendum).
 *
 * <p>The first account bootstraps the organization and becomes {@code org_admin}; afterwards user
 * creation/lifecycle operations require an active org admin. Passwords are BCrypt hashes only.
 */
@Service
public class UserService {

  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
  private static final SecureRandom RANDOM = new SecureRandom();

  private final AppUserRepository appUserRepository;
  private final OrganizationService organizationService;
  private final AuditService auditService;
  private final PasswordEncoder passwordEncoder;
  private final AuthorizationService authorizationService;
  private final RoleAssignmentRepository roleAssignmentRepository;
  private final SessionService sessionService;

  public UserService(
      AppUserRepository appUserRepository,
      OrganizationService organizationService,
      AuditService auditService,
      PasswordEncoder passwordEncoder,
      AuthorizationService authorizationService,
      RoleAssignmentRepository roleAssignmentRepository,
      SessionService sessionService) {
    this.appUserRepository = appUserRepository;
    this.organizationService = organizationService;
    this.auditService = auditService;
    this.passwordEncoder = passwordEncoder;
    this.authorizationService = authorizationService;
    this.roleAssignmentRepository = roleAssignmentRepository;
    this.sessionService = sessionService;
  }

  /** Internal/test provisioning primitive; HTTP and admin flows use {@link #createAsAdmin}. */
  @Transactional
  public AppUser create(String username, String displayName, String password) {
    return createInternal(null, username, displayName, password, null, AppUser.ACCOUNT_HUMAN);
  }

  /** Create a human/agent account. The first user may bootstrap without an actor. */
  @Transactional
  public AppUser createAsAdmin(AppUser actor, String username, String displayName, String password,
      String email, String accountType) {
    if (appUserRepository.count() > 0) {
      authorizationService.requireOrgAdmin(actor);
    }
    return createInternal(actor == null ? null : actor.getId(), username, displayName, password,
        email, accountType);
  }

  private AppUser createInternal(String actorUserId, String username, String displayName,
      String password, String email, String accountType) {
    String normalized = username == null ? "" : username.trim();
    if (normalized.length() < 2) {
      throw ApiException.badRequest("username must contain at least 2 characters");
    }
    if (password == null || password.length() < 8) {
      throw ApiException.badRequest("password must contain at least 8 characters");
    }
    if (appUserRepository.findByUsername(normalized).isPresent()) {
      throw ApiException.conflict("username already exists: " + normalized);
    }
    String type = normalizeAccountType(accountType);
    String normalizedEmail = normalizeEmail(email);
    long existing = appUserRepository.count();
    AppUser user = new AppUser(
        Ids.newId(),
        organizationService.currentOrgId(),
        normalized,
        displayName == null || displayName.isBlank() ? normalized : displayName.trim(),
        passwordEncoder.encode(password),
        AppUser.STATUS_ACTIVE,
        Ids.nowIso(),
        normalizedEmail,
        type,
        false);
    appUserRepository.save(user);
    if (existing == 0) {
      grantOrgAdminInternal(user);
    }
    auditService.record(actorUserId == null ? user.getId() : actorUserId, "user.create",
        "app_user", user.getId(), "{\"accountType\":\"" + type + "\"}");
    return user;
  }

  @Transactional(readOnly = true)
  public List<AppUser> list() {
    return appUserRepository.findByOrgIdOrderByCreatedAtAsc(organizationService.currentOrgId());
  }

  /** Full user administration list (org admin only). */
  @Transactional(readOnly = true)
  public List<AppUser> listAsAdmin(AppUser actor) {
    authorizationService.requireOrgAdmin(actor);
    return list();
  }

  /** Minimal active-user directory for project owners adding members. */
  @Transactional(readOnly = true)
  public List<AppUser> directory() {
    return list().stream().filter(AppUser::isActive).toList();
  }

  @Transactional(readOnly = true)
  public AppUser requireUser(String userId) {
    return appUserRepository.findById(userId)
        .orElseThrow(() -> ApiException.notFound("user not found: " + userId));
  }

  @Transactional(readOnly = true)
  public long count() {
    return appUserRepository.count();
  }

  @Transactional
  public AppUser disable(AppUser actor, String userId) {
    authorizationService.requireOrgAdmin(actor);
    AppUser target = requireUser(userId);
    if (target.getId().equals(actor.getId())) {
      throw ApiException.conflict("cannot disable your own account");
    }
    if (target.isActive() && authorizationService.isOrgAdmin(target)
        && countActiveOrgAdmins() <= 1) {
      throw ApiException.conflict("cannot disable the last active organization admin");
    }
    if (target.isActive()) {
      target.disable();
      appUserRepository.save(target);
      sessionService.revokeAllForUser(target.getId());
      auditService.record(actor.getId(), "user.disable", "app_user", target.getId(), null);
    }
    return target;
  }

  @Transactional
  public AppUser enable(AppUser actor, String userId) {
    authorizationService.requireOrgAdmin(actor);
    AppUser target = requireUser(userId);
    if (!target.isActive()) {
      target.enable();
      appUserRepository.save(target);
      auditService.record(actor.getId(), "user.enable", "app_user", target.getId(), null);
    }
    return target;
  }

  /**
   * Reset a human account to a one-time temporary password. All sessions are revoked; the next login
   * is restricted to password change until the user rotates it.
   */
  @Transactional
  public String resetPassword(AppUser actor, String userId) {
    authorizationService.requireOrgAdmin(actor);
    AppUser target = requireUser(userId);
    if (target.isAgent()) {
      throw ApiException.badRequest("agent accounts cannot log in and have no password to reset");
    }
    String temporaryPassword = generateTemporaryPassword();
    target.changePasswordHash(passwordEncoder.encode(temporaryPassword));
    target.markPasswordResetRequired();
    appUserRepository.save(target);
    sessionService.revokeAllForUser(target.getId());
    auditService.record(actor.getId(), "user.password_reset", "app_user", target.getId(), null);
    return temporaryPassword;
  }

  @Transactional
  public AppUser setOrgAdmin(AppUser actor, String userId, boolean orgAdmin) {
    authorizationService.requireOrgAdmin(actor);
    AppUser target = requireUser(userId);
    boolean currently = authorizationService.isOrgAdmin(target);
    if (orgAdmin && !currently) {
      grantOrgAdminInternal(target);
      auditService.record(actor.getId(), "user.org_role_change", "app_user", target.getId(),
          "{\"role\":\"org_admin\",\"granted\":true}");
    } else if (!orgAdmin && currently) {
      if (target.isActive() && countActiveOrgAdmins() <= 1) {
        throw ApiException.conflict("cannot remove the last active organization admin");
      }
      revokeOrgAdminInternal(target);
      auditService.record(actor.getId(), "user.org_role_change", "app_user", target.getId(),
          "{\"role\":\"org_admin\",\"granted\":false}");
    }
    return target;
  }

  @Transactional(readOnly = true)
  public long countActiveOrgAdmins() {
    String orgId = organizationService.currentOrgId();
    return roleAssignmentRepository
        .findByRoleAndScopeTypeAndScopeId(AuthorizationService.ORG_ADMIN,
            AuthorizationService.SCOPE_ORGANIZATION, orgId)
        .stream()
        .map(RoleAssignment::getUserId)
        .distinct()
        .filter(userId -> appUserRepository.findById(userId).filter(AppUser::isActive).isPresent())
        .count();
  }

  private void grantOrgAdminInternal(AppUser user) {
    String orgId = organizationService.currentOrgId();
    if (!roleAssignmentRepository.existsByUserIdAndRoleAndScopeTypeAndScopeId(
        user.getId(), AuthorizationService.ORG_ADMIN, AuthorizationService.SCOPE_ORGANIZATION,
        orgId)) {
      roleAssignmentRepository.save(new RoleAssignment(
          Ids.newId(), user.getId(), AuthorizationService.ORG_ADMIN,
          AuthorizationService.SCOPE_ORGANIZATION, orgId, Ids.nowIso()));
    }
  }

  private void revokeOrgAdminInternal(AppUser user) {
    roleAssignmentRepository.findByUserIdAndRoleAndScopeTypeAndScopeId(
            user.getId(), AuthorizationService.ORG_ADMIN, AuthorizationService.SCOPE_ORGANIZATION,
            organizationService.currentOrgId())
        .ifPresent(roleAssignmentRepository::delete);
  }

  private static String normalizeAccountType(String accountType) {
    if (accountType == null || accountType.isBlank()) {
      return AppUser.ACCOUNT_HUMAN;
    }
    String normalized = accountType.trim().toLowerCase(Locale.ROOT);
    if (!AppUser.ACCOUNT_HUMAN.equals(normalized) && !AppUser.ACCOUNT_AGENT.equals(normalized)) {
      throw ApiException.badRequest("unsupported account type: " + accountType);
    }
    return normalized;
  }

  private static String normalizeEmail(String email) {
    if (email == null || email.isBlank()) {
      return null;
    }
    String normalized = email.trim();
    if (!EMAIL.matcher(normalized).matches()) {
      throw ApiException.badRequest("email is not valid: " + normalized);
    }
    return normalized;
  }

  private static String generateTemporaryPassword() {
    byte[] raw = new byte[18];
    RANDOM.nextBytes(raw);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
  }
}
