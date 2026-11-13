package com.mammoth.domain.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves and validates the Mammoth data root (ADR-0002, ADR-0004, ADR-0008).
 *
 * <p>Rules: the root is configurable (application setting / {@code MAMMOTH_DATA_ROOT}); the default
 * is the OS application-data directory. A root inside the DSH home, inside the source checkout, on
 * a network path or in a cloud-synced directory is rejected at startup (fail fast), because SQLite
 * WAL locking is not reliable on those filesystems and business data must not live in runtime or
 * code locations. A test/controlled migration may override the network/cloud check with
 * {@code mammoth.data-root.allow-unsupported=true} (logged as a warning).
 */
@Component
public class DataRootResolver {

  /** Filesystem types that are known to be remote (SQLite is unsafe on them). */
  private static final Set<String> REMOTE_FILE_SYSTEM_TYPES = Set.of(
      "smb", "smbfs", "cifs", "nfs", "nfs4", "afpfs", "webdav", "davfs", "fuse.sshfs", "sshfs");

  /** Directory names that indicate a cloud-sync client root (segment match, case-insensitive). */
  private static final Set<String> CLOUD_SYNC_SEGMENTS = Set.of(
      "onedrive", "dropbox", "google drive", "googledrive", "icloud drive", "box sync",
      "nutstore", "坚果云");

  private final Path configuredRoot;
  private final boolean allowUnsupported;

  @Autowired
  public DataRootResolver(
      @Value("${mammoth.data-root:}") String configuredRoot,
      @Value("${mammoth.data-root.allow-unsupported:false}") boolean allowUnsupported) {
    this(resolve(configuredRoot, allowUnsupported), allowUnsupported);
  }

  /** Test/legacy constructor: strict mode (no unsupported-root override). */
  public DataRootResolver(String configuredRoot) {
    this(configuredRoot, false);
  }

  DataRootResolver(Path root, boolean allowUnsupported) {
    this.configuredRoot = root;
    this.allowUnsupported = allowUnsupported;
  }

  public Path root() {
    return configuredRoot;
  }

  public boolean isAllowUnsupported() {
    return allowUnsupported;
  }

  /** Strict resolution (no unsupported-root override); used by the legacy constructor/tests. */
  static Path resolve(String configured) {
    return resolve(configured, false);
  }

  /** Resolve the data root or throw with an actionable message when the location is forbidden. */
  static Path resolve(String configured, boolean allowUnsupported) {
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
    if (!allowUnsupported) {
      requireLocalDisk(normalized);
    }
    return normalized;
  }

  /**
   * Reject UNC paths, known cloud-sync directories and known remote filesystem types (ADR-0008).
   * Unknown filesystem types are accepted to avoid false positives (tmpfs/overlay/containers).
   */
  private static void requireLocalDisk(Path root) {
    String text = root.toString();
    if (text.startsWith("\\\\") || text.startsWith("//")) {
      throw new IllegalStateException(
          "mammoth.data-root must be a local fixed disk, not a UNC path: " + root
              + ". Set mammoth.data-root.allow-unsupported=true only for a controlled/test"
              + " environment.");
    }
    for (Path segment : root) {
      if (CLOUD_SYNC_SEGMENTS.contains(segment.toString().toLowerCase(Locale.ROOT))) {
        throw new IllegalStateException(
            "mammoth.data-root must not be inside a cloud-synced directory (found '"
                + segment + "' in " + root + "). SMB/cloud sync is unsafe for SQLite WAL."
                + " Set mammoth.data-root.allow-unsupported=true only for a controlled/test"
                + " environment.");
      }
    }
    Path existing = root;
    while (existing != null && !Files.exists(existing)) {
      existing = existing.getParent();
    }
    if (existing == null) {
      return;
    }
    try {
      String type = Files.getFileStore(existing).type().toLowerCase(Locale.ROOT);
      if (REMOTE_FILE_SYSTEM_TYPES.contains(type)) {
        throw new IllegalStateException(
            "mammoth.data-root must be a local fixed disk, not a network filesystem ('"
                + type + "'): " + root + ". Set mammoth.data-root.allow-unsupported=true only for"
                + " a controlled/test environment.");
      }
    } catch (IOException ignored) {
      // Cannot determine the filesystem type: UNC/cloud checks above are the best effort.
    }
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
