package com.mammoth.domain.web;

import com.mammoth.domain.service.UserService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bootstrap state for the first-run setup wizard (UI-SPEC P02). */
@RestController
@RequestMapping("/api/setup")
public class SetupController {

  private final UserService userService;

  public SetupController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping("/status")
  public Map<String, Object> status() {
    long users = userService.count();
    return Map.of("initialized", users > 0, "userCount", users);
  }
}
