package com.mammoth.domain.agentwrite;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ADR-0015 P1: hash helpers for the append-only controlled-write audit chain.
 *
 * <p>Each entry hash covers the previous entry hash plus the audited facts, so editing or deleting
 * a row breaks the chain. Chain scope is per proposal; entries are appended by the single approval
 * service process.
 */
public final class AuditHash {

  private AuditHash() {
  }

  /** Canonical entry hash: sha256(prevHash || proposalId || stage || actor || outcome || at). */
  public static String entryHash(String prevHash, String proposalId, String stage,
      String actorKind, String actorUserId, String outcome, String at) {
    String canonical = Stream.of(prevHash, proposalId, stage, actorKind, actorUserId, outcome, at)
        .map(AuditHash::nullSafe)
        .collect(Collectors.joining("|"));
    return sha256Hex(canonical);
  }

  /** Stable fingerprint of an audited request/payload for the chain and deny evidence. */
  public static String fingerprint(String payload) {
    return sha256Hex(nullSafe(payload));
  }

  private static String nullSafe(String value) {
    return value == null ? "" : value;
  }

  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 unavailable", error);
    }
  }
}
