package com.mammoth.domain.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * HTTP security for the M1 domain service (ADR-0006).
 *
 * <ul>
 *   <li>opaque server-side session cookie (no Spring Security HTTP session, no basic auth);</li>
 *   <li>CSRF protection with a readable {@code XSRF-TOKEN} cookie for the SPA;</li>
 *   <li>only health/meta/login (and first-user bootstrap) are public;</li>
 *   <li>unauthenticated API calls get 401, authorized-but-forbidden decisions stay in the domain.</li>
 * </ul>
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, SessionAuthenticationFilter sessionFilter) throws Exception {
    http
        .csrf(csrf -> csrf
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            // SPA clients read the XSRF-TOKEN cookie and send it back verbatim in X-XSRF-TOKEN;
            // the default XOR-masked handler would require server-rendered tokens instead.
            .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
            // Login has no session yet; CSRF applies to all authenticated mutations.
            .ignoringRequestMatchers("/api/auth/login"))
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            // No servlet session is used, so session-authentication strategies (notably CSRF token
            // rotation that clears the XSRF cookie on every authenticated request) must not run.
            .sessionAuthenticationStrategy((authentication, request, response) -> { }))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/health", "/api/meta", "/api/auth/login", "/api/auth/csrf",
                "/api/setup/status").permitAll()
            // First-user bootstrap: the controller enforces "only while the org has no users".
            .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
            .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/error").permitAll()
            // SPA deep links must load the app shell; the SPA then renders login/setup or redirects.
            .requestMatchers("/login", "/setup", "/projects", "/projects/**", "/settings", "/403",
                "/404").permitAll()
            // Forced password change: a temporary-password session may only read itself, change its
            // password, log out or fetch a CSRF token. Everything else requires a full session.
            .requestMatchers("/api/auth/me", "/api/auth/password", "/api/auth/logout")
                .hasAnyAuthority(SessionAuthenticationFilter.ROLE_USER,
                    SessionAuthenticationFilter.ROLE_PASSWORD_RESET_REQUIRED)
            .requestMatchers("/api/**").hasAuthority(SessionAuthenticationFilter.ROLE_USER)
            .anyRequest().authenticated())
        .exceptionHandling(handling -> handling.authenticationEntryPoint(
            (request, response, error) -> response.sendError(401, "authentication required")))
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        // Resolve the session cookie *after* SessionManagementFilter so session-authentication
        // strategies (which rotate/kill the CSRF cookie) never see filter-established auth, and
        // *before* AuthorizationFilter so the request is authorized with the resolved user.
        .addFilterBefore(sessionFilter,
            org.springframework.security.web.access.intercept.AuthorizationFilter.class);
    return http.build();
  }
}
