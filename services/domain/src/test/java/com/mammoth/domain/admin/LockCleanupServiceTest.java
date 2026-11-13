package com.mammoth.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mammoth.domain.web.ApiException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ADR-0008: stale lock cleanup must never delete a lock held by a running writer. */
class LockCleanupServiceTest {

  @TempDir
  Path tempDir;

  private Path root() {
    return tempDir.toAbsolutePath().normalize();
  }

  @Test
  void refusesWhileAWriterHoldsTheLockAndClearsAfterRelease() throws Exception {
    Path lockFile = root().resolve(".mammoth-instance.lock");
    Path holderFile = root().resolve(".mammoth-instance-holder");
    Files.writeString(holderFile, "pid=1234\nhost=test\nstartedAt=now\nrole=writer\n");

    try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE,
        StandardOpenOption.WRITE)) {
      FileLock lock = channel.tryLock();
      assertThat(lock).isNotNull();
      try {
        assertThatThrownBy(() -> new LockCleanupService().clear(root()))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("in use")
            .hasMessageContaining("pid=1234");
        assertThat(Files.exists(lockFile)).isTrue();
      } finally {
        lock.release();
      }
    }

    LockCleanupService.Result result = new LockCleanupService().clear(root());
    assertThat(result.deleted()).isTrue();
    assertThat(result.holderDeleted()).isTrue();
    assertThat(result.holder()).contains("pid=1234");
    assertThat(Files.exists(lockFile)).isFalse();
    assertThat(Files.exists(holderFile)).isFalse();
  }

  @Test
  void clearsStaleFilesWhenNoProcessHoldsTheLock() throws Exception {
    Path lockFile = root().resolve(".mammoth-instance.lock");
    Path holderFile = root().resolve(".mammoth-instance-holder");
    Files.write(lockFile, new byte[0]);
    Files.writeString(holderFile, "pid=999\nhost=test\nrole=writer\n", StandardCharsets.UTF_8);

    LockCleanupService.Result result = new LockCleanupService().clear(root());

    assertThat(result.deleted()).isTrue();
    assertThat(result.holderDeleted()).isTrue();
    assertThat(Files.exists(lockFile)).isFalse();
    assertThat(Files.exists(holderFile)).isFalse();
  }

  @Test
  void reportsWhenThereIsNothingToClear() throws Exception {
    LockCleanupService.Result result = new LockCleanupService().clear(root());
    assertThat(result.deleted()).isFalse();
    assertThat(result.holder()).isEmpty();
  }

  @Test
  void rejectsAMissingDataRoot() {
    assertThatThrownBy(() -> new LockCleanupService().clear(tempDir.resolve("missing")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("does not exist");
  }
}
