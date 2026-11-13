package com.mammoth.domain.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the opaque session cookie into a Spring Security authentication (principal = user id).
 * Sessions live server-side, so logout/disable takes effect immediately for every request.
 */
@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

  public static final String ROLE_USER = "ROLE_USER";
  public static final String ROLE_PASSWORD_RESET_REQUIRED = "ROLE_PASSWORD_RESET_REQUIRED";

  private final SessionService sessionService;

  public SessionAuthenticationFilter(SessionService sessionService) {
    this.sessionService = sessionService;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String token = sessionToken(request);
    if (token != null) {
      sessionService.validateSession(token).ifPresent(session -> {
        String authority = session.passwordResetRequired()
            ? ROLE_PASSWORD_RESET_REQUIRED
            : ROLE_USER;
        var authentication = new UsernamePasswordAuthenticationToken(
            session.userId(), null, List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
      });
    }
    filterChain.doFilter(request, response);
  }

  static String sessionToken(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (SessionService.COOKIE_NAME.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }
}
