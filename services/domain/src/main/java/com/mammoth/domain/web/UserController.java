package com.mammoth.domain.web;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.security.CurrentUser;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.service.UserService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local account provisioning and lifecycle (ADR-0006 + 2026-11-13 M4 addendum).
 *
 * <p>The first account is a bootstrap step (only while the organization has no users); afterwards
 * user creation/lifecycle operations require an active {@code org_admin}. Project owners use the
 * minimal directory to add existing users as members.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

  private final UserService userService;
  private final AuthorizationService authorizationService;

  public UserController(UserService userService, AuthorizationService authorizationService) {
    this.userService = userService;
    this.authorizationService = authorizationService;
  }

  public record CreateUserRequest(String username, String displayName, String password, String email,
      String accountType) {
  }

  public record UserResponse(String id, String username, String displayName, String email,
      String accountType, String status, boolean orgAdmin, String createdAt) {

    static UserResponse from(AppUser user, boolean orgAdmin) {
      return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(),
          user.getEmail(), user.getAccountType(), user.getStatus(), orgAdmin, user.getCreatedAt());
    }
  }

  public record DirectoryEntry(String id, String username, String displayName) {

    static DirectoryEntry from(AppUser user) {
      return new DirectoryEntry(user.getId(), user.getUsername(), user.getDisplayName());
    }
  }

  public record BootstrapResponse(String id, String username, boolean bootstrap, boolean orgAdmin) {
  }

  public record ResetPasswordResponse(String temporaryPassword) {
  }

  public record OrgAdminRequest(Boolean orgAdmin) {
  }

  @PostMapping
  public BootstrapResponse create(@RequestBody CreateUserRequest request) {
    boolean bootstrap = userService.count() == 0;
    AppUser actor = null;
    if (!bootstrap) {
      CurrentUser.requireFullSession();
      actor = CurrentUser.require(authorizationService);
    }
    AppUser user = userService.createAsAdmin(actor, request.username(), request.displayName(),
        request.password(), request.email(), request.accountType());
    return new BootstrapResponse(user.getId(), user.getUsername(), bootstrap,
        authorizationService.isOrgAdmin(user));
  }

  @GetMapping
  public List<UserResponse> list() {
    AppUser actor = CurrentUser.require(authorizationService);
    return userService.listAsAdmin(actor).stream()
        .map(user -> UserResponse.from(user, authorizationService.isOrgAdmin(user)))
        .toList();
  }

  /** Minimal active-user directory for project owners selecting members. */
  @GetMapping("/directory")
  public List<DirectoryEntry> directory() {
    CurrentUser.require(authorizationService);
    return userService.directory().stream().map(DirectoryEntry::from).toList();
  }

  @PostMapping("/{userId}/disable")
  public UserResponse disable(@PathVariable String userId) {
    AppUser actor = CurrentUser.require(authorizationService);
    AppUser user = userService.disable(actor, userId);
    return UserResponse.from(user, authorizationService.isOrgAdmin(user));
  }

  @PostMapping("/{userId}/enable")
  public UserResponse enable(@PathVariable String userId) {
    AppUser actor = CurrentUser.require(authorizationService);
    AppUser user = userService.enable(actor, userId);
    return UserResponse.from(user, authorizationService.isOrgAdmin(user));
  }

  @PostMapping("/{userId}/reset-password")
  public ResetPasswordResponse resetPassword(@PathVariable String userId) {
    AppUser actor = CurrentUser.require(authorizationService);
    return new ResetPasswordResponse(userService.resetPassword(actor, userId));
  }

  @PatchMapping("/{userId}/org-admin")
  public UserResponse setOrgAdmin(@PathVariable String userId,
      @RequestBody OrgAdminRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    AppUser user = userService.setOrgAdmin(actor, userId, Boolean.TRUE.equals(request.orgAdmin()));
    return UserResponse.from(user, authorizationService.isOrgAdmin(user));
  }
}
