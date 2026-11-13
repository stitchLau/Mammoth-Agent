package com.mammoth.domain.git;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Creates disposable Git repositories for tests; skips the test when git is unavailable. */
public final class GitFixtures {

  private GitFixtures() {
  }

  public static void assumeGitAvailable() {
    try {
      Process process = new ProcessBuilder("git", "--version")
          .redirectErrorStream(true).start();
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      assumeTrue(process.waitFor() == 0, "git is not available: " + output);
    } catch (Exception error) {
      assumeTrue(false, "git is not available: " + error.getMessage());
    }
  }

  public static Path initRepo(Path directory) throws Exception {
    Files.createDirectories(directory);
    git(directory, "init", "-b", "main");
    git(directory, "config", "user.email", "tester@example.com");
    git(directory, "config", "user.name", "Mammoth Tester");
    return directory;
  }

  public static String commit(Path repository, String fileName, String content, String message)
      throws Exception {
    Path file = repository.resolve(fileName);
    if (file.getParent() != null) {
      Files.createDirectories(file.getParent());
    }
    Files.writeString(file, content, StandardCharsets.UTF_8);
    git(repository, "add", "-A");
    git(repository, "commit", "-m", message);
    return head(repository);
  }

  public static String head(Path repository) throws Exception {
    return git(repository, "rev-parse", "HEAD").trim();
  }

  public static void remote(Path repository, String url) throws Exception {
    git(repository, "remote", "add", "origin", url);
  }

  public static String git(Path repository, String... args) throws Exception {
    List<String> command = new ArrayList<>();
    command.add("git");
    command.add("-C");
    command.add(repository.toString());
    command.addAll(Arrays.asList(args));
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int exit = process.waitFor();
    if (exit != 0) {
      throw new IllegalStateException(
          "git " + String.join(" ", args) + " failed (" + exit + "): " + output);
    }
    return output;
  }

  public static Path createRepository(Path parent, String name) throws Exception {
    Path repository = parent.resolve(name);
    Files.createDirectories(repository);
    return repository;
  }

  public static void deleteRecursively(Path path) throws IOException {
    if (path == null || !Files.exists(path)) {
      return;
    }
    try (var walk = Files.walk(path)) {
      walk.sorted(java.util.Comparator.reverseOrder()).forEach(file -> {
        try {
          Files.deleteIfExists(file);
        } catch (IOException ignored) {
          // best effort in test cleanup
        }
      });
    }
  }
}
