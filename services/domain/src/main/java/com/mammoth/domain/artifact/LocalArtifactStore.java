package com.mammoth.domain.artifact;

import com.mammoth.domain.config.DataRootResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Local content-addressed artifact store (ADR-0004):
 * {@code <dataRoot>/artifacts/<sha[0:2]>/<sha[2:4]>/<sha256>}.
 *
 * <p>Writes go through a temp file + atomic move, then the object is marked read-only; reads verify
 * SHA-256 before returning data. Identical bytes are stored once regardless of how many projects
 * reference them (metadata remains per project).
 */
@Component
public class LocalArtifactStore implements ArtifactStore {

  private static final Logger log = LoggerFactory.getLogger(LocalArtifactStore.class);

  private final Path artifactsRoot;
  private final Path dataRoot;

  public LocalArtifactStore(DataRootResolver resolver) {
    this.dataRoot = resolver.root();
    this.artifactsRoot = dataRoot.resolve("artifacts");
  }

  @Override
  public StoredArtifact put(byte[] content) {
    String sha = sha256Hex(content);
    Path target = objectPath(sha);
    try {
      if (Files.exists(target)) {
        return new StoredArtifact(sha, relativePath(sha), Files.size(target));
      }
      Files.createDirectories(target.getParent());
      Path temp = Files.createTempFile(target.getParent(), sha, ".tmp");
      try {
        Files.write(temp, content);
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
      } finally {
        Files.deleteIfExists(temp);
      }
      setReadOnly(target);
      if (!sha.equals(sha256Hex(Files.readAllBytes(target)))) {
        throw new IllegalStateException("artifact write verification failed for " + sha);
      }
      log.info("stored artifact {} ({} bytes)", sha, content.length);
      return new StoredArtifact(sha, relativePath(sha), content.length);
    } catch (IOException error) {
      throw new IllegalStateException("cannot store artifact: " + error.getMessage(), error);
    }
  }

  @Override
  public byte[] read(String sha256) {
    Path object = objectPath(sha256);
    if (!Files.exists(object)) {
      throw new IllegalArgumentException("artifact content not found: " + sha256);
    }
    try {
      byte[] content = Files.readAllBytes(object);
      String actual = sha256Hex(content);
      if (!actual.equals(sha256)) {
        throw new IllegalStateException(
            "artifact content hash mismatch for " + sha256 + " (actual " + actual + ")");
      }
      return content;
    } catch (IOException error) {
      throw new IllegalStateException("cannot read artifact " + sha256 + ": " + error.getMessage(), error);
    }
  }

  @Override
  public boolean exists(String sha256) {
    return Files.exists(objectPath(sha256));
  }

  @Override
  public void delete(String sha256) {
    Path object = objectPath(sha256);
    try {
      setWritable(object);
      Files.deleteIfExists(object);
    } catch (IOException error) {
      throw new IllegalStateException("cannot delete artifact " + sha256 + ": " + error.getMessage(), error);
    }
  }

  /** Path relative to the Mammoth data root, used as metadata {@code storage_path}. */
  public String relativePath(String sha256) {
    return dataRoot.relativize(objectPath(sha256)).toString().replace('\\', '/');
  }

  private Path objectPath(String sha256) {
    if (sha256 == null || sha256.length() != 64) {
      throw new IllegalArgumentException("invalid artifact hash: " + sha256);
    }
    String normalized = sha256.toLowerCase();
    return artifactsRoot.resolve(normalized.substring(0, 2))
        .resolve(normalized.substring(2, 4))
        .resolve(normalized);
  }

  private static void setReadOnly(Path path) {
    try {
      Files.setAttribute(path, "dos:readonly", true);
    } catch (Exception ignored) {
      // non-Windows platforms enforce immutability through the app layer only
    }
  }

  private static void setWritable(Path path) {
    try {
      Files.setAttribute(path, "dos:readonly", false);
    } catch (Exception ignored) {
      // ignore
    }
  }

  public static String sha256Hex(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 unavailable", error);
    }
  }
}
