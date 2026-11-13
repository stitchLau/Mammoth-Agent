package com.mammoth.domain.config;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Boots the Mammoth data root (ADR-0002/0004/0008): creates the layout, verifies it is writable, and
 * fails fast with an actionable message. Business data must never land in the DSH home, the source
 * checkout, or the installation directory (validated by {@link DataRootResolver}).
 *
 * <pre>
 * &lt;dataRoot&gt;/
 *   db/          SQLite database + migrations metadata
 *   artifacts/   content-addressed immutable artifacts (ADR-0004)
 *   cache/       disposable derived data (indexes may live here or under db/)
 *   audit/       local append-only MCP/agent audit journal (ADR-0008 addendum)
 * </pre>
 *
 * <p>Read-only instances (the MCP stdio entrypoint, ADR-0008) must not create directories or write
 * probe files: they require an already-initialized root and fail with an actionable message
 * otherwise.
 */
@Component
public class DataRootBootstrap {

  private static final Logger log = LoggerFactory.getLogger(DataRootBootstrap.class);
  private static final String PROBE_FILE = ".mammoth-write-probe";

  private final DataRootResolver resolver;
  private final boolean readOnly;

  @Autowired
  public DataRootBootstrap(DataRootResolver resolver,
      @Value("${mammoth.data-root.read-only:false}") boolean readOnly) {
    this.resolver = resolver;
    this.readOnly = readOnly;
  }

  /** Test/legacy constructor: writable writer mode. */
  public DataRootBootstrap(DataRootResolver resolver) {
    this(resolver, false);
  }

  @PostConstruct
  public void onStartup() {
    Path root = initialize();
    log.info("Mammoth data root ready ({}): {}", readOnly ? "read-only" : "writer", root);
  }

  /** Create the directory layout and prove the root is writable, or verify an existing root. */
  public Path initialize() {
    Path root = resolver.root();
    if (readOnly) {
      verifyExisting(root);
      return root;
    }
    try {
      Files.createDirectories(root);
      for (String child : new String[] {"db", "artifacts", "cache", "audit"}) {
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

  private void verifyExisting(Path root) {
    if (!Files.isDirectory(root)) {
      throw new IllegalStateException(
          "Mammoth data root does not exist (" + root + "). Read-only mode does not create data;"
              + " start the web/management entrypoint once to initialize it, or set"
              + " MAMMOTH_DATA_ROOT to an initialized data root.");
    }
    if (!Files.isDirectory(root.resolve("db"))) {
      throw new IllegalStateException(
          "Mammoth data root is not initialized (" + root + "): missing db/. Start the"
              + " web/management entrypoint once to run migrations, then retry the read-only"
              + " process.");
    }
  }

  public boolean isReadOnly() {
    return readOnly;
  }

  public Path root() {
    return resolver.root();
  }

  public Path databaseFile() {
    return resolver.root().resolve("db").resolve("mammoth.db");
  }
}
