package com.mammoth.domain.admin;

import com.mammoth.domain.web.ApiException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Safely removes a stale instance-lock file (ADR-0008). The lock file itself is only removed when no
 * process currently holds it; an active writer is reported with its holder metadata and nothing is
 * deleted. Pure file operation: no Spring context and no data-root initialization.
 */
public final class LockCleanupService {

  public record Result(Path root, boolean deleted, boolean holderDeleted, String holder) {
  }

  public Result clear(Path dataRoot) {
    Path root = dataRoot == null ? null : dataRoot.toAbsolutePath().normalize();
    if (root == null || !Files.isDirectory(root)) {
      throw ApiException.badRequest("data root does not exist: " + dataRoot);
    }
    Path lockFile = root.resolve(".mammoth-instance.lock");
    Path holderFile = root.resolve(".mammoth-instance-holder");
    if (!Files.isRegularFile(lockFile, LinkOption.NOFOLLOW_LINKS)) {
      return new Result(root, false, false, readHolder(holderFile));
    }
    String holder = readHolder(holderFile);
    try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE)) {
      FileLock lock;
      try {
        lock = channel.tryLock();
      } catch (OverlappingFileLockException sameJvm) {
        lock = null;
      }
      if (lock == null) {
        throw ApiException.conflict(
            "the data root is in use by a running Mammoth instance"
                + (holder.isBlank() ? "" : " (" + holder + ")")
                + "; stop that process before clearing the lock");
      }
      lock.release();
    } catch (ApiException error) {
      throw error;
    } catch (IOException error) {
      throw ApiException.conflict(
          "cannot acquire the data root lock (a process may still be using it"
              + (holder.isBlank() ? "" : ": " + holder) + "): " + error.getMessage());
    }
    try {
      Files.deleteIfExists(lockFile);
      boolean holderDeleted = Files.deleteIfExists(holderFile);
      return new Result(root, true, holderDeleted, holder);
    } catch (IOException error) {
      throw new IllegalStateException(
          "cannot delete stale lock files under " + root + ": " + error.getMessage(), error);
    }
  }

  private static String readHolder(Path holderFile) {
    try {
      if (!Files.isRegularFile(holderFile, LinkOption.NOFOLLOW_LINKS)) {
        return "";
      }
      return Files.readString(holderFile, StandardCharsets.UTF_8).trim()
          .replace('\n', ';').replace('\r', ' ');
    } catch (IOException ignored) {
      return "";
    }
  }
}
