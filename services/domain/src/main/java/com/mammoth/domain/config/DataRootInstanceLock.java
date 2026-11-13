package com.mammoth.domain.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Single-writer guard for one Mammoth data root (ADR-0008).
 *
 * <p>The web/management entrypoint holds an exclusive OS file lock on
 * {@code <dataRoot>/.mammoth-instance.lock} for its lifetime. A second writer fails fast instead of
 * corrupting the SQLite database. Windows file locks are mandatory, so the holder metadata is
 * written to a sibling file ({@code .mammoth-instance-holder}) that a blocked process can read.
 *
 * <p>The OS releases the lock when the process dies, so a leftover lock file never blocks the next
 * start (it is re-locked and the holder file overwritten). Read-only instances (MCP, ADR-0008) do
 * not take the lock and do not create either file.
 */
@Component
public class DataRootInstanceLock {

  static final String LOCK_FILE_NAME = ".mammoth-instance.lock";
  static final String HOLDER_FILE_NAME = ".mammoth-instance-holder";
  private static final Logger log = LoggerFactory.getLogger(DataRootInstanceLock.class);

  private final Path root;
  private final boolean readOnly;
  private FileChannel channel;
  private FileLock lock;

  @Autowired
  public DataRootInstanceLock(DataRootBootstrap bootstrap,
      @Value("${mammoth.data-root.read-only:false}") boolean readOnly) {
    this(bootstrap.root(), readOnly);
  }

  /** Test constructor: explicit root and mode. */
  DataRootInstanceLock(Path root, boolean readOnly) {
    this.root = root.toAbsolutePath().normalize();
    this.readOnly = readOnly;
  }

  @PostConstruct
  public void acquire() {
    if (readOnly) {
      log.info("read-only instance: data root lock not acquired (ADR-0008)");
      return;
    }
    if (lock != null && lock.isValid()) {
      return;
    }
    Path lockFile = lockFile();
    try {
      channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    } catch (IOException error) {
      throw new IllegalStateException(
          "cannot open the Mammoth instance lock " + lockFile + ": " + error.getMessage(), error);
    }
    try {
      lock = channel.tryLock();
    } catch (OverlappingFileLockException sameJvm) {
      lock = null;
    } catch (IOException error) {
      closeQuietly();
      throw new IllegalStateException(
          "cannot lock the Mammoth data root " + root + ": " + error.getMessage(), error);
    }
    if (lock == null) {
      String holder = readHolder();
      closeQuietly();
      throw new IllegalStateException(
          "another Mammoth instance is already using the data root " + root
              + (holder.isBlank() ? "" : " (" + holder + ")")
              + ". Stop that process first; if it crashed, delete " + lockFile + " and retry."
              + " Two writers on one SQLite data root are not supported (ADR-0008).");
    }
    writeHolder();
    log.info("data root instance lock acquired: {}", lockFile);
  }

  @PreDestroy
  public void release() {
    closeQuietly();
  }

  Path lockFile() {
    return root.resolve(LOCK_FILE_NAME);
  }

  Path holderFile() {
    return root.resolve(HOLDER_FILE_NAME);
  }

  boolean isHeld() {
    return lock != null && lock.isValid();
  }

  boolean isReadOnly() {
    return readOnly;
  }

  private void writeHolder() {
    String metadata = holderMetadata();
    try {
      Files.writeString(holderFile(), metadata, StandardCharsets.UTF_8,
          StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
    } catch (IOException error) {
      throw new IllegalStateException(
          "cannot write the Mammoth instance holder file " + holderFile() + ": "
              + error.getMessage(), error);
    }
  }

  private String readHolder() {
    try {
      String content = Files.readString(holderFile(), StandardCharsets.UTF_8).trim();
      return content.replace('\n', ';').replace('\r', ' ');
    } catch (IOException ignored) {
      return "";
    }
  }

  private static String holderMetadata() {
    return "pid=" + ProcessHandle.current().pid()
        + "\nhost=" + hostName()
        + "\nstartedAt=" + Instant.now().truncatedTo(ChronoUnit.SECONDS)
        + "\nrole=writer\n";
  }

  private static String hostName() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (Exception ignored) {
      return "unknown";
    }
  }

  private void closeQuietly() {
    try {
      if (lock != null && lock.isValid()) {
        lock.release();
      }
    } catch (IOException ignored) {
      // closing the channel below also releases the lock
    }
    try {
      if (channel != null && channel.isOpen()) {
        channel.close();
      }
    } catch (IOException ignored) {
      // best effort
    }
    lock = null;
    channel = null;
  }
}
