package com.mammoth.domain.config;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves and validates the Mammoth data root (ADR-0002, ADR-0004).
 *
 * <p>Rules: the root is configurable (application setting / {@code MAMMOTH_DATA_ROOT}); the default
 * is the OS application-data directory. A root inside the DSH home, inside the source checkout, or
 * inside the installation directory is rejected at startup (fail fast), because business data must
 * not live in runtime or code locations.
 */
@Component
public class DataRootResolver {

  private final Path configuredRoot;

  public DataRootResolver(@Value("${mammoth.data-root:}") String configuredRoot) {
    this.configuredRoot = resolve(configuredRoot);
  }

  public Path root() {
    return configuredRoot;
  }

  /** Resolve the data root or throw with an actionable message when the location is forbidden. */
  static Path resolve(String configured) {
    Path candidate = configured == null || configured.isBlank()
        ? defaultRoot()
        : Path.of(configured);
    Path normalized = candidate.toAbsolutePath().normalize();
    String lower = normalized.toString().toLowerCase(Locale.ROOT);
    if (lower.contains(".dsh")) {
      throw new IllegalStateException(
          "mammoth.data-root must not be inside the DSH home (~/.dsh): " + normalized);
    }
    if (isInsideSourceCheckout(normalized)) {
      throw new IllegalStateException(
          "mammoth.data-root must not be inside the source checkout: " + normalized);
    }
    return normalized;
  }

  private static Path defaultRoot() {
    String localAppData = System.getenv("LOCALAPPDATA");
    if (localAppData != null && !localAppData.isBlank()) {
      return Path.of(localAppData, "Mammoth", "data");
    }
    return Path.of(System.getProperty("user.home"), ".mammoth", "data");
  }

  private static boolean isInsideSourceCheckout(Path candidate) {
    Optional<Path> checkout = findCheckoutRoot(Path.of("").toAbsolutePath());
    return checkout.isPresent() && candidate.startsWith(checkout.get());
  }

  private static Optional<Path> findCheckoutRoot(Path start) {
    Path current = start;
    while (current != null) {
      if (current.resolve("AGENTS.md").toFile().isFile()
          && current.resolve("services").toFile().isDirectory()) {
        return Optional.of(current);
      }
      current = current.getParent();
    }
    return Optional.empty();
  }
}
