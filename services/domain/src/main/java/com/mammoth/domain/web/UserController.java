package com.mammoth.domain.web;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.security.CurrentUser;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.service.UserService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local account provisioning (ADR-0006).
 *
 * <p>The very first account is a bootstrap step (only while the organization has no users); after
 * that, creating users requires an authenticated caller. There are no built-in accounts or default
 * passwords.
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

  public record CreateUserRequest(String username, String displayName, String password) {
  }

  public record UserResponse(String id, String username, String displayName, String status,
      String createdAt) {

    static UserResponse from(AppUser user) {
      return new UserResponse(
          user.getId(), user.getUsername(), user.getDisplayName(), user.getStatus(), user.getCreatedAt());
    }
  }

  public record BootstrapResponse(String id, String username, boolean bootstrap) {
  }

  @PostMapping
  public BootstrapResponse create(@RequestBody CreateUserRequest request) {
    boolean bootstrap = userService.count() == 0;
    if (!bootstrap) {
      CurrentUser.require(authorizationService);
    }
    AppUser user = userService.create(request.username(), request.displayName(), request.password());
    return new BootstrapResponse(user.getId(), user.getUsername(), bootstrap);
  }

  @GetMapping
  public List<UserResponse> list() {
    CurrentUser.require(authorizationService);
    return userService.list().stream().map(UserResponse::from).toList();
  }
}
