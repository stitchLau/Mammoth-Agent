package com.mammoth.domain.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** M3 S1 acceptance: one writer per data root (ADR-0008). */
class DataRootInstanceLockTest {

  @TempDir
  Path tempDir;

  private Path root() {
    return tempDir.toAbsolutePath().normalize();
  }

  @Test
  void secondWriterIsRejectedWithHolderMetadataAndReleaseAllowsRetry() throws Exception {
    DataRootInstanceLock first = new DataRootInstanceLock(root(), false);
    first.acquire();
    try {
      assertThat(first.isHeld()).isTrue();
      Path lockFile = root().resolve(".mammoth-instance.lock");
      Path holderFile = root().resolve(".mammoth-instance-holder");
      assertThat(Files.exists(lockFile)).isTrue();
      assertThat(Files.isRegularFile(holderFile)).isTrue();
      String holder = Files.readString(holderFile);
      assertThat(holder).contains("pid=").contains("role=writer").contains("startedAt=");

      DataRootInstanceLock second = new DataRootInstanceLock(root(), false);
      assertThatThrownBy(second::acquire)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("another Mammoth instance")
          .hasMessageContaining("pid=")
          .hasMessageContaining(".mammoth-instance.lock");
      assertThat(second.isHeld()).isFalse();
    } finally {
      first.release();
    }
    assertThat(first.isHeld()).isFalse();

    DataRootInstanceLock retry = new DataRootInstanceLock(root(), false);
    retry.acquire();
    try {
      assertThat(retry.isHeld()).isTrue();
    } finally {
      retry.release();
    }
  }

  @Test
  void readOnlyInstanceDoesNotCreateTheLockFile() {
    DataRootInstanceLock lock = new DataRootInstanceLock(root(), true);
    lock.acquire();

    assertThat(lock.isHeld()).isFalse();
    assertThat(Files.exists(root().resolve(".mammoth-instance.lock"))).isFalse();
  }
}
