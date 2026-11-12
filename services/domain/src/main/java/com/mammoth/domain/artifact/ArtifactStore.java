package com.mammoth.domain.artifact;

/**
 * Immutable artifact storage seam (ADR-0004). The M1 implementation is a local content-addressed
 * directory; a server deployment can replace it with object storage without touching the domain.
 */
public interface ArtifactStore {

  /** Result of storing bytes: content hash, path relative to the data root, byte size. */
  record StoredArtifact(String sha256, String relativePath, long sizeBytes) {
  }

  /** Store bytes idempotently: identical content is written once (deduplicated by SHA-256). */
  StoredArtifact put(byte[] content);

  /** Read bytes and verify the content hash before returning them. */
  byte[] read(String sha256);

  boolean exists(String sha256);

  /** Remove an object. Reserved for authorized maintenance flows; normal domain logic never calls it. */
  void delete(String sha256);
}
