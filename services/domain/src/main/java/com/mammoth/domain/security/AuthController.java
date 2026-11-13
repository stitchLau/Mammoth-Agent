package com.mammoth.domain.security;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.AppUserRepository;
import com.mammoth.domain.service.AuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Login/logout/current-user endpoints backing the opaque session cookie (ADR-0006). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthenticationService authenticationService;
  private final SessionService sessionService;
  private final AppUserRepository appUserRepository;
  private final AccountService accountService;
  private final AuthorizationService authorizationService;

  public AuthController(
      AuthenticationService authenticationService,
      SessionService sessionService,
      AppUserRepository appUserRepository,
      AccountService accountService,
      AuthorizationService authorizationService) {
    this.authenticationService = authenticationService;
    this.sessionService = sessionService;
    this.appUserRepository = appUserRepository;
    this.accountService = accountService;
    this.authorizationService = authorizationService;
  }

  public record LoginRequest(String username, String password) {
  }

  public record ProfileRequest(String displayName, String email) {
  }

  public record PasswordRequest(String currentPassword, String newPassword) {
  }

  public record MeResponse(String id, String username, String displayName, String email,
      String accountType, boolean passwordResetRequired, boolean orgAdmin) {

    static MeResponse from(AppUser user, boolean orgAdmin) {
      return new MeResponse(user.getId(), user.getUsername(), user.getDisplayName(),
          user.getEmail(), user.getAccountType(), user.isPasswordResetRequired(), orgAdmin);
    }
  }

  @GetMapping("/csrf")
  public Map<String, Object> csrf(CsrfToken token) {
    // Reading the token forces deferred token creation, which also writes the XSRF-TOKEN cookie.
    return Map.of("token", token.getToken());
  }

  @PostMapping("/login")
  public MeResponse login(@RequestBody LoginRequest request, HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    String token = authenticationService.login(request.username(), request.password());
    ResponseCookie cookie = ResponseCookie.from(SessionService.COOKIE_NAME, token)
        .httpOnly(true)
        .secure(httpRequest.isSecure())
        .sameSite("Lax")
        .path("/")
        .maxAge(Duration.ofSeconds(SessionService.TTL_SECONDS))
        .build();
    httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    return appUserRepository.findByUsername(request.username().trim())
        .map(user -> MeResponse.from(user, authorizationService.isOrgAdmin(user)))
        .orElseThrow();
  }

  @PostMapping("/logout")
  public Map<String, Object> logout(HttpServletRequest httpRequest) {
    sessionService.revoke(SessionAuthenticationFilter.sessionToken(httpRequest));
    return Map.of("status", "logged_out");
  }

  @GetMapping("/me")
  public MeResponse me() {
    AppUser user = appUserRepository.findById(CurrentUser.id())
        .orElseThrow(() -> com.mammoth.domain.web.ApiException.unauthorized("unknown user"));
    return MeResponse.from(user, authorizationService.isOrgAdmin(user));
  }

  @PutMapping("/profile")
  public MeResponse profile(@RequestBody ProfileRequest request) {
    AppUser user = accountService.updateProfile(
        CurrentUser.id(), request.displayName(), request.email());
    return MeResponse.from(user, authorizationService.isOrgAdmin(user));
  }

  @PutMapping("/password")
  public Map<String, Object> password(@RequestBody PasswordRequest request) {
    accountService.changePassword(CurrentUser.id(), request.currentPassword(), request.newPassword());
    return Map.of("status", "password_changed");
  }
}
