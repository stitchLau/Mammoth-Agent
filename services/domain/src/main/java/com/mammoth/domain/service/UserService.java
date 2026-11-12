package com.mammoth.domain.service;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.AppUserRepository;
import com.mammoth.domain.support.Ids;
import com.mammoth.domain.web.ApiException;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local account provisioning (ADR-0006). There are no built-in accounts and no default password:
 * the first user is created explicitly through the API in the M1 single-machine setup.
 */
@Service
public class UserService {

  private final AppUserRepository appUserRepository;
  private final OrganizationService organizationService;
  private final AuditService auditService;
  private final PasswordEncoder passwordEncoder;

  public UserService(
      AppUserRepository appUserRepository,
      OrganizationService organizationService,
      AuditService auditService,
      PasswordEncoder passwordEncoder) {
    this.appUserRepository = appUserRepository;
    this.organizationService = organizationService;
    this.auditService = auditService;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public AppUser create(String username, String displayName, String password) {
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
    AppUser user = new AppUser(
        Ids.newId(),
        organizationService.currentOrgId(),
        normalized,
        displayName == null || displayName.isBlank() ? normalized : displayName.trim(),
        passwordEncoder.encode(password),
        "active",
        Ids.nowIso());
    appUserRepository.save(user);
    auditService.record(user.getId(), "user.create", "app_user", user.getId(), null);
    return user;
  }

  @Transactional(readOnly = true)
  public List<AppUser> list() {
    return appUserRepository.findByOrgIdOrderByCreatedAtAsc(organizationService.currentOrgId());
  }
}
