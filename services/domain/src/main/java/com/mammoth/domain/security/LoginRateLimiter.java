package com.mammoth.domain.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * In-memory login throttling (ADR-0006 risk mitigation): BCrypt verification costs ~200 ms, so
 * repeated failures must be blocked before they can be used for brute force or resource exhaustion.
 * M1 is single-process; a shared store replaces this when the service becomes multi-instance.
 */
@Component
public class LoginRateLimiter {

  private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
  private final int maxAttempts;
  private final Duration window;

  public LoginRateLimiter(
      @Value("${mammoth.security.login-max-attempts:5}") int maxAttempts,
      @Value("${mammoth.security.login-window-seconds:900}") long windowSeconds) {
    this.maxAttempts = maxAttempts;
    this.window = Duration.ofSeconds(windowSeconds);
  }

  /** Fail with 429 when the caller exceeded the failure budget inside the window. */
  public void checkAllowed(String key) {
    Deque<Instant> attempts = failures.get(key);
    if (attempts == null) {
      return;
    }
    synchronized (attempts) {
      prune(attempts);
      if (attempts.size() >= maxAttempts) {
        throw new com.mammoth.domain.web.ApiException(
            org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
            "too many failed login attempts; retry later");
      }
    }
  }

  public void recordFailure(String key) {
    Deque<Instant> attempts = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
    synchronized (attempts) {
      prune(attempts);
      attempts.addLast(Instant.now());
    }
  }

  public void clear(String key) {
    failures.remove(key);
  }

  private void prune(Deque<Instant> attempts) {
    Instant cutoff = Instant.now().minus(window);
    while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
      attempts.removeFirst();
    }
  }
}
