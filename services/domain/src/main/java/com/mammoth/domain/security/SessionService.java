package com.mammoth.domain.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Server-side opaque session tokens (ADR-0006): 256-bit random token in an HttpOnly cookie; the
 * database stores only its SHA-256, sessions expire after 8 hours and can be revoked immediately.
 */
@Service
public class SessionService {

  public static final String COOKIE_NAME = "MAMMOTH_SESSION";
  public static final long TTL_SECONDS = 8L * 60 * 60;

  /** Authenticated session facts needed by the HTTP filter (ADR-0006 M4 addendum). */
  public record SessionInfo(String userId, boolean passwordResetRequired) {
  }

  private final JdbcTemplate jdbcTemplate;
  private final SecureRandom random = new SecureRandom();

  public SessionService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional
  public String create(String userId) {
    return create(userId, false);
  }

  @Transactional
  public String create(String userId, boolean passwordResetRequired) {
    byte[] raw = new byte[32];
    random.nextBytes(raw);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    long now = Instant.now().getEpochSecond();
    jdbcTemplate.update(
        "INSERT INTO session (token_hash, user_id, created_at, expires_at, password_reset_required)"
            + " VALUES (?,?,?,?,?)",
        sha256(token), userId, now, now + TTL_SECONDS, passwordResetRequired ? 1 : 0);
    return token;
  }

  /** Returns the user id for a valid, unexpired, unrevoked session token. */
  public Optional<String> validate(String token) {
    return validateSession(token).map(SessionInfo::userId);
  }

  /** Returns the full session facts, including the forced-password-change marker. */
  public Optional<SessionInfo> validateSession(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    long now = Instant.now().getEpochSecond();
    var rows = jdbcTemplate.query(
        "SELECT user_id, password_reset_required FROM session"
            + " WHERE token_hash=? AND revoked_at IS NULL AND expires_at>?",
        (resultSet, rowNum) -> new SessionInfo(
            resultSet.getString("user_id"),
            resultSet.getInt("password_reset_required") != 0),
        sha256(token), now);
    return rows.stream().findFirst();
  }

  @Transactional
  public void revoke(String token) {
    if (token == null || token.isBlank()) {
      return;
    }
    jdbcTemplate.update(
        "UPDATE session SET revoked_at=? WHERE token_hash=?",
        Instant.now().getEpochSecond(), sha256(token));
  }

  @Transactional
  public void revokeAllForUser(String userId) {
    jdbcTemplate.update(
        "UPDATE session SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL",
        Instant.now().getEpochSecond(), userId);
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception error) {
      throw new IllegalStateException("SHA-256 unavailable", error);
    }
  }
}
