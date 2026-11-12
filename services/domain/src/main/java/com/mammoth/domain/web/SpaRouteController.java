package com.mammoth.domain.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA deep-link fallback (UI-SPEC §9): the SPA owns its client-side routes, so a browser refresh on
 * {@code /projects/...} must return index.html instead of a 404. API paths are untouched.
 */
@Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SpaRouteController {

  @GetMapping({
      "/login",
      "/setup",
      "/projects",
      "/projects/**",
      "/settings",
      "/403",
      "/404"
  })
  public String spa() {
    return "forward:/index.html";
  }
}
