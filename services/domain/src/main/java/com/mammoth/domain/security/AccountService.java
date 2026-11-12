package com.mammoth.domain.security;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.AppUserRepository;
import com.mammoth.domain.service.AuditService;
import com.mammoth.domain.web.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service account settings (UI-SPEC P09): display name and password change. A password change
 * revokes every session, so a stolen cookie cannot outlive a credential rotation.
 */
@Service
public class AccountService {

  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final SessionService sessionService;
  private final AuditService auditService;

  public AccountService(
      AppUserRepository appUserRepository,
      PasswordEncoder passwordEncoder,
      SessionService sessionService,
      AuditService auditService) {
    this.appUserRepository = appUserRepository;
    this.passwordEncoder = passwordEncoder;
    this.sessionService = sessionService;
    this.auditService = auditService;
  }

  @Transactional
  public AppUser updateDisplayName(String userId, String displayName) {
    String normalized = displayName == null ? "" : displayName.trim();
    if (normalized.isEmpty()) {
      throw ApiException.badRequest("displayName must not be empty");
    }
    AppUser user = appUserRepository.findById(userId)
        .orElseThrow(() -> ApiException.unauthorized("unknown user"));
    user.rename(normalized);
    appUserRepository.save(user);
    auditService.record(userId, "auth.profile_update", "app_user", userId, null);
    return user;
  }

  @Transactional
  public void changePassword(String userId, String currentPassword, String newPassword) {
    AppUser user = appUserRepository.findById(userId)
        .orElseThrow(() -> ApiException.unauthorized("unknown user"));
    if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
      throw ApiException.unauthorized("current password is incorrect");
    }
    if (newPassword == null || newPassword.length() < 8) {
      throw ApiException.badRequest("password must contain at least 8 characters");
    }
    if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
      throw ApiException.badRequest("new password must differ from the current password");
    }
    user.changePasswordHash(passwordEncoder.encode(newPassword));
    appUserRepository.save(user);
    sessionService.revokeAllForUser(userId);
    auditService.record(userId, "auth.password_change", "app_user", userId, null);
  }
}
