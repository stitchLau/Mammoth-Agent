package com.mammoth.domain.git;

import com.mammoth.domain.web.ApiException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Native Git CLI implementation of {@link GitAdapter} (ADR-0007).
 *
 * <p>Only the read-only whitelist is used: {@code rev-parse}, {@code status --porcelain=v2},
 * {@code diff --name-status}, {@code diff --numstat}, {@code merge-base --is-ancestor} and
 * {@code remote get-url}. The process runs with prompts disabled, optional locks disabled, file
 * name quoting off and an explicit exact-path {@code safe.directory} entry for the registered repo.
 * Timeout 10 s, output cap 1 MiB.
 */
@Component
public class LocalGitAdapter implements GitAdapter {

  static final Duration TIMEOUT = Duration.ofSeconds(10);
  static final int MAX_OUTPUT_BYTES = 1024 * 1024;
  private static final Pattern COMMIT_PATTERN = Pattern.compile("[0-9a-fA-F]{7,40}");

  @Override
  public Snapshot inspect(Path repositoryPath) {
    Path directory = requireDirectory(repositoryPath);
    String root = required(directory, "rev-parse", "--show-toplevel").trim();
    Path rootPath = Path.of(root);
    String head = required(rootPath, "rev-parse", "HEAD").trim();
    String branchRef = required(rootPath, "rev-parse", "--abbrev-ref", "HEAD").trim();
    String status = required(rootPath, "status", "--porcelain=v2", "--branch");
    boolean dirty = Arrays.stream(status.split("\n"))
        .map(String::trim)
        .anyMatch(line -> !line.isEmpty() && !line.startsWith("#"));
    boolean detached = "HEAD".equals(branchRef) || status.contains("# branch.head (detached)");
    boolean worktree = Files.isRegularFile(rootPath.resolve(".git"));
    String remote = remoteUrl(rootPath);
    return new Snapshot(rootPath.toAbsolutePath().normalize().toString(), head,
        detached ? null : branchRef, detached, dirty, worktree, remote);
  }

  @Override
  public List<FileChange> diff(Path repositoryPath, String fromCommit, String toCommit) {
    Path directory = requireDirectory(repositoryPath);
    verifyCommit(directory, fromCommit);
    verifyCommit(directory, toCommit);
    String nameStatus = required(directory, "diff", "--name-status", fromCommit, toCommit, "--");
    String numStat = required(directory, "diff", "--numstat", fromCommit, toCommit, "--");

    Map<String, int[]> stats = new LinkedHashMap<>();
    for (String line : numStat.split("\n")) {
      if (line.isBlank()) {
        continue;
      }
      String[] parts = line.split("\t", 3);
      if (parts.length < 3) {
        continue;
      }
      int additions = parseInt(parts[0]);
      int deletions = parseInt(parts[1]);
      stats.put(parts[2], new int[] {additions, deletions});
    }

    List<FileChange> changes = new ArrayList<>();
    for (String line : nameStatus.split("\n")) {
      if (line.isBlank()) {
        continue;
      }
      String[] parts = line.split("\t");
      if (parts.length < 2) {
        continue;
      }
      String rawType = parts[0];
      String changeType = rawType.isEmpty() ? "M" : rawType.substring(0, 1);
      String path;
      String oldPath = null;
      if ("R".equals(changeType) || "C".equals(changeType)) {
        oldPath = parts[1];
        path = parts.length > 2 ? parts[2] : parts[1];
      } else {
        path = parts[1];
      }
      int[] counts = stats.getOrDefault(path, new int[] {0, 0});
      changes.add(new FileChange(path, changeType, oldPath, counts[0], counts[1]));
    }
    return changes;
  }

  @Override
  public boolean isAncestor(Path repositoryPath, String ancestor, String descendant) {
    Path directory = requireDirectory(repositoryPath);
    requireCommitArgument(ancestor);
    requireCommitArgument(descendant);
    Result result = run(directory, "merge-base", "--is-ancestor", ancestor, descendant);
    if (result.exitCode == 0) {
      return true;
    }
    if (result.exitCode == 1) {
      return false;
    }
    throw ApiException.conflict("git merge-base failed: " + firstLine(result.output));
  }

  @Override
  public void verifyCommit(Path repositoryPath, String commit) {
    requireCommitArgument(commit);
    Result result = run(repositoryPath, "rev-parse", "--verify", commit + "^{commit}");
    if (result.exitCode != 0) {
      throw ApiException.badRequest(
          "commit " + commit + " does not exist in " + repositoryPath + ": "
              + firstLine(result.output));
    }
  }

  private static void requireCommitArgument(String commit) {
    if (commit == null || !COMMIT_PATTERN.matcher(commit).matches()) {
      throw ApiException.badRequest("invalid commit id: " + commit);
    }
  }

  private static Path requireDirectory(Path repositoryPath) {
    if (repositoryPath == null) {
      throw ApiException.badRequest("repository path is required");
    }
    Path absolute = repositoryPath.toAbsolutePath().normalize();
    if (!Files.isDirectory(absolute)) {
      throw ApiException.badRequest("repository path does not exist: " + absolute);
    }
    return absolute;
  }

  private String required(Path directory, String... args) {
    Result result = run(directory, args);
    if (result.exitCode != 0) {
      throw ApiException.badRequest(
          "not a usable git repository (" + directory + "): " + firstLine(result.output));
    }
    return result.output;
  }

  private String remoteUrl(Path directory) {
    Result result = run(directory, "remote", "get-url", "origin");
    if (result.exitCode != 0 || result.output.isBlank()) {
      return null;
    }
    return sanitizeRemoteUrl(result.output.trim());
  }

  /** Strip http(s) userinfo so credentials embedded in a remote URL are never stored or logged. */
  static String sanitizeRemoteUrl(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      URI uri = URI.create(raw);
      if (uri.getScheme() != null && uri.getUserInfo() != null) {
        URI sanitized = new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(),
            uri.getPath(), uri.getQuery(), uri.getFragment());
        return sanitized.toString();
      }
    } catch (Exception ignored) {
      // scp-like SSH remote (git@host:path) or unusual syntax: keep as-is (user is not a secret)
    }
    return raw;
  }

  private static int parseInt(String value) {
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  private static String firstLine(String output) {
    String trimmed = output == null ? "" : output.trim();
    int newline = trimmed.indexOf('\n');
    return newline < 0 ? trimmed : trimmed.substring(0, newline);
  }

  private record Result(int exitCode, String output) {
  }

  private Result run(Path directory, String... args) {
    List<String> command = new ArrayList<>();
    command.add("git");
    command.add("-C");
    command.add(directory.toString());
    command.add("--no-optional-locks");
    command.add("-c");
    command.add("core.quotepath=false");
    command.add("-c");
    command.add("core.fsmonitor=false");
    command.add("-c");
    command.add("safe.directory=" + directory.toAbsolutePath().normalize());
    command.addAll(Arrays.asList(args));

    ProcessBuilder builder = new ProcessBuilder(command);
    builder.redirectErrorStream(true);
    builder.environment().put("GIT_TERMINAL_PROMPT", "0");
    builder.environment().put("GIT_OPTIONAL_LOCKS", "0");
    Process process;
    try {
      process = builder.start();
    } catch (IOException error) {
      throw ApiException.conflict(
          "git executable is not available; install Git or disable the Git integration: "
              + error.getMessage());
    }
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    Thread reader = new Thread(() -> drainCapped(process.getInputStream(), buffer), "git-output");
    reader.setDaemon(true);
    reader.start();
    try {
      boolean finished = process.waitFor(TIMEOUT.toMillis(),
          java.util.concurrent.TimeUnit.MILLISECONDS);
      if (!finished) {
        process.destroyForcibly();
        reader.join(1_000);
        throw ApiException.conflict(
            "git " + String.join(" ", args) + " timed out after " + TIMEOUT.toSeconds() + "s");
      }
      reader.join(2_000);
      return new Result(process.exitValue(), outputOf(buffer));
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
      throw ApiException.conflict("git command was interrupted");
    }
  }

  private static void drainCapped(InputStream stream, ByteArrayOutputStream buffer) {
    byte[] chunk = new byte[8192];
    int total = 0;
    try {
      int read;
      while ((read = stream.read(chunk)) != -1) {
        if (total < MAX_OUTPUT_BYTES) {
          int allowed = Math.min(read, MAX_OUTPUT_BYTES - total);
          synchronized (buffer) {
            buffer.write(chunk, 0, allowed);
          }
          total += allowed;
        }
        // keep draining so the child process can exit even when output is truncated
      }
    } catch (IOException ignored) {
      // process ended; the exit code is reported by run()
    }
  }

  private static String outputOf(ByteArrayOutputStream buffer) {
    String text;
    synchronized (buffer) {
      text = buffer.toString(StandardCharsets.UTF_8);
    }
    if (text.getBytes(StandardCharsets.UTF_8).length >= MAX_OUTPUT_BYTES) {
      text += "\n[output truncated at " + MAX_OUTPUT_BYTES + " bytes]";
    }
    return text;
  }
}
