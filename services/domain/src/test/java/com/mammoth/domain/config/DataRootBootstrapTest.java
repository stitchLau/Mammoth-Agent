package com.mammoth.domain.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataRootBootstrapTest {

  @TempDir
  Path tempDir;

  @Test
  void createsLayoutAndProvesWritability() {
    DataRootBootstrap bootstrap = new DataRootBootstrap(new DataRootResolver(tempDir.toString()));
    Path root = bootstrap.initialize();

    assertThat(root).isEqualTo(tempDir.toAbsolutePath().normalize());
    assertThat(Files.isDirectory(root.resolve("db"))).isTrue();
    assertThat(Files.isDirectory(root.resolve("artifacts"))).isTrue();
    assertThat(Files.isDirectory(root.resolve("cache"))).isTrue();
    assertThat(Files.exists(root.resolve(".mammoth-write-probe"))).isFalse();
    assertThat(bootstrap.databaseFile().getParent()).isEqualTo(root.resolve("db"));
  }

  @Test
  void failsFastWhenRootIsAFile() throws Exception {
    Path file = tempDir.resolve("not-a-directory");
    Files.writeString(file, "x");
    DataRootBootstrap bootstrap = new DataRootBootstrap(new DataRootResolver(file.toString()));

    assertThatThrownBy(bootstrap::initialize)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not usable");
  }
}
