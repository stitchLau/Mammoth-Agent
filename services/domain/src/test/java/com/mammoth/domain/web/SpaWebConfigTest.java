package com.mammoth.domain.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The SPA is optional at runtime: resolution must fall back gracefully when no build exists. */
class SpaWebConfigTest {

  @TempDir
  Path tempDir;

  @Test
  void resolvesExplicitDirectoryContainingIndexHtml() throws Exception {
    Files.writeString(tempDir.resolve("index.html"), "<html>ok</html>");

    assertThat(SpaWebConfig.resolveDist(tempDir.toString())).isEqualTo(tempDir.toAbsolutePath().normalize().toString());
  }

  @Test
  void returnsNullWhenNoCandidateContainsIndexHtml() {
    Path empty = tempDir.resolve("empty");
    assertThat(SpaWebConfig.resolveDist(empty.toString())).isNull();
  }

  @Test
  void resolvesNothingWhenRepositoryHasNoBuild() {
    // No assumption about the developer machine: either a dist exists and resolves, or it is null.
    String resolved = SpaWebConfig.resolveDist("");
    if (resolved != null) {
      assertThat(Path.of(resolved).resolve("index.html")).exists();
    }
  }

  @Test
  void hasIndexDetectsFiles() throws Exception {
    Files.writeString(tempDir.resolve("index.html"), "x");
    assertThat(SpaWebConfig.hasIndex(tempDir.toString())).isTrue();
    assertThat(SpaWebConfig.hasIndex(tempDir.resolve("missing").toString())).isFalse();
  }
}
