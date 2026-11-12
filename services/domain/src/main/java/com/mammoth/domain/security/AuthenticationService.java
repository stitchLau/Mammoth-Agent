package com.mammoth.domain.security;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.AppUserRepository;
import com.mammoth.domain.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Local password authentication for the single-machine M1 deployment (ADR-0006). */
@Service
public class AuthenticationService {

  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final SessionService sessionService;
  private final LoginRateLimiter rateLimiter;

  public AuthenticationService(
      AppUserRepository appUserRepository,
      PasswordEncoder passwordEncoder,
      SessionService sessionService,
      LoginRateLimiter rateLimiter) {
    this.appUserRepository = appUserRepository;
    this.passwordEncoder = passwordEncoder;
    this.sessionService = sessionService;
    this.rateLimiter = rateLimiter;
  }

  /** Authenticate and start a session; returns the raw session token for the cookie. */
  @Transactional
  public String login(String username, String password) {
    String key = username == null ? "" : username.trim().toLowerCase();
    rateLimiter.checkAllowed(key);
    if (username == null || username.isBlank() || password == null || password.isEmpty()) {
      rateLimiter.recordFailure(key);
      throw ApiException.badRequest("username and password are required");
    }
    AppUser user = appUserRepository.findByUsername(username.trim()).orElse(null);
    if (user == null || !user.isActive() || !passwordEncoder.matches(password, user.getPasswordHash())) {
      rateLimiter.recordFailure(key);
      throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid username or password");
    }
    rateLimiter.clear(key);
    return sessionService.create(user.getId());
  }

  @Transactional
  public void logout(String token) {
    sessionService.revoke(token);
  }
}
