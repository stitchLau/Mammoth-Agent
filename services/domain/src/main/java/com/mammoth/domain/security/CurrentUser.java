package com.mammoth.domain.security;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.service.AuthorizationService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Reads the authenticated caller from the security context (session filter puts the user id there). */
public final class CurrentUser {

  private CurrentUser() {
  }

  public static String id() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof String userId)) {
      throw com.mammoth.domain.web.ApiException.unauthorized("authentication required");
    }
    return userId;
  }

  /** Resolve the caller as an active user (also used by controllers before domain calls). */
  public static AppUser require(AuthorizationService authorizationService) {
    return authorizationService.requireActiveUser(id());
  }

  /** True when the current session has the full (non-temporary-password) authority. */
  public static boolean hasFullSession() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null && authentication.getAuthorities().stream()
        .anyMatch(authority -> SessionAuthenticationFilter.ROLE_USER.equals(authority.getAuthority()));
  }

  /** Endpoints reachable without a full session must call this before mutating anything. */
  public static void requireFullSession() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof String)) {
      throw com.mammoth.domain.web.ApiException.unauthorized("authentication required");
    }
    if (!hasFullSession()) {
      throw com.mammoth.domain.web.ApiException.forbidden(
          "password change required before this action");
    }
  }
}
