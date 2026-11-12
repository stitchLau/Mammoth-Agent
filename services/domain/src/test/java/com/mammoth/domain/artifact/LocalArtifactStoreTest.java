package com.mammoth.domain.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mammoth.domain.config.DataRootResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalArtifactStoreTest {

  @TempDir
  Path tempDir;

  private LocalArtifactStore store() {
    return new LocalArtifactStore(new DataRootResolver(tempDir.toString()));
  }

  @Test
  void storesContentAddressedAndDeduplicates() {
    LocalArtifactStore store = store();
    byte[] content = "Mammoth 收货流程说明".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    ArtifactStore.StoredArtifact first = store.put(content);
    ArtifactStore.StoredArtifact second = store.put(content.clone());

    assertThat(second.sha256()).isEqualTo(first.sha256());
    assertThat(second.relativePath()).isEqualTo(first.relativePath());
    assertThat(store.exists(first.sha256())).isTrue();
    assertThat(first.relativePath()).matches("artifacts/[0-9a-f]{2}/[0-9a-f]{2}/[0-9a-f]{64}");
    assertThat(store.read(first.sha256())).isEqualTo(content);
  }

  @Test
  void storedObjectIsReadOnlyWhereThePlatformSupportsIt() {
    LocalArtifactStore store = store();
    byte[] content = "immutable".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    ArtifactStore.StoredArtifact stored = store.put(content);

    Path object = tempDir.resolve(stored.relativePath());
    boolean readOnly;
    try {
      readOnly = Boolean.TRUE.equals(Files.getAttribute(object, "dos:readonly"));
    } catch (Exception unsupported) {
      // Linux/macOS do not expose the DOS read-only attribute; immutability is app-layer only there.
      return;
    }
    assertThat(readOnly).isTrue();
    assertThatThrownBy(() -> Files.write(object, new byte[] {1, 2, 3}))
        .isInstanceOf(java.io.IOException.class);
  }

  @Test
  void detectsCorruptedContent() throws Exception {
    LocalArtifactStore store = store();
    byte[] content = "verify me".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    ArtifactStore.StoredArtifact stored = store.put(content);

    Path object = tempDir.resolve(stored.relativePath());
    try {
      Files.setAttribute(object, "dos:readonly", false);
    } catch (Exception unsupported) {
      // attribute unavailable: the file is already writable
    }
    Files.write(object, "tampered".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    assertThatThrownBy(() -> store.read(stored.sha256()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("hash mismatch");
  }
}
