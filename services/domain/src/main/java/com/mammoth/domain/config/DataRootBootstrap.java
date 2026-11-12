package com.mammoth.domain.config;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Boots the Mammoth data root (ADR-0002/0004): creates the layout, verifies it is writable, and
 * fails fast with an actionable message. Business data must never land in the DSH home, the source
 * checkout, or the installation directory (validated by {@link DataRootResolver}).
 *
 * <pre>
 * &lt;dataRoot&gt;/
 *   db/          SQLite database + migrations metadata
 *   artifacts/   content-addressed immutable artifacts (ADR-0004)
 *   cache/       disposable derived data (indexes may live here or under db/)
 * </pre>
 */
@Component
public class DataRootBootstrap {

  private static final Logger log = LoggerFactory.getLogger(DataRootBootstrap.class);
  private static final String PROBE_FILE = ".mammoth-write-probe";

  private final DataRootResolver resolver;

  public DataRootBootstrap(DataRootResolver resolver) {
    this.resolver = resolver;
  }

  @PostConstruct
  public void onStartup() {
    Path root = initialize();
    log.info("Mammoth data root ready: {}", root);
  }

  /** Create the directory layout and prove the root is writable. Public for tests. */
  public Path initialize() {
    Path root = resolver.root();
    try {
      Files.createDirectories(root);
      for (String child : new String[] {"db", "artifacts", "cache"}) {
        Files.createDirectories(root.resolve(child));
      }
      Path probe = root.resolve(PROBE_FILE);
      Files.writeString(probe, "mammoth-write-probe", StandardCharsets.UTF_8);
      String readBack = Files.readString(probe, StandardCharsets.UTF_8);
      Files.deleteIfExists(probe);
      if (!"mammoth-write-probe".equals(readBack)) {
        throw new IllegalStateException("data root write probe mismatch: " + root);
      }
    } catch (IOException error) {
      throw new IllegalStateException(
          "Mammoth data root is not usable (" + root + "): " + error.getMessage()
              + ". Set MAMMOTH_DATA_ROOT or mammoth.data-root to a writable directory outside"
              + " the source checkout and the DSH home.", error);
    }
    return root;
  }

  public Path databaseFile() {
    return resolver.root().resolve("db").resolve("mammoth.db");
  }
}
