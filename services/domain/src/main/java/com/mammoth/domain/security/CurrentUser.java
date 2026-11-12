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
}
