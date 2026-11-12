package com.mammoth.domain.web;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.service.UserService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Local account provisioning for the M1 single-machine setup (ADR-0006). */
@RestController
@RequestMapping("/api/users")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
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

  @PostMapping
  public UserResponse create(@RequestBody CreateUserRequest request) {
    AppUser user = userService.create(request.username(), request.displayName(), request.password());
    return UserResponse.from(user);
  }

  @GetMapping
  public List<UserResponse> list() {
    return userService.list().stream().map(UserResponse::from).toList();
  }
}
