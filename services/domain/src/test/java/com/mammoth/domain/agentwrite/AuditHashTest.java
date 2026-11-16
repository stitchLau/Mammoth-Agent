package com.mammoth.domain.agentwrite;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ADR-0015 P1: the write-audit hash chain must be deterministic and tamper-evident. */
class AuditHashTest {

  private static final String PREV = "a".repeat(64);

  @Test
  void entryHashIsDeterministic() {
    String first = AuditHash.entryHash(PREV, "p1", "approve", "human", "u1", "ok",
        "2026-11-16T12:00:00Z");
    String second = AuditHash.entryHash(PREV, "p1", "approve", "human", "u1", "ok",
        "2026-11-16T12:00:00Z");
    assertThat(first).isEqualTo(second).hasSize(64);
  }

  @Test
  void changingAnyAuditedFieldChangesTheHash() {
    String base = AuditHash.entryHash(PREV, "p1", "approve", "human", "u1", "ok",
        "2026-11-16T12:00:00Z");
    assertThat(AuditHash.entryHash(PREV, "p2", "approve", "human", "u1", "ok",
        "2026-11-16T12:00:00Z")).isNotEqualTo(base);
    assertThat(AuditHash.entryHash(PREV, "p1", "deny", "human", "u1", "ok",
        "2026-11-16T12:00:00Z")).isNotEqualTo(base);
    assertThat(AuditHash.entryHash(PREV, "p1", "approve", "human", "u2", "ok",
        "2026-11-16T12:00:00Z")).isNotEqualTo(base);
    assertThat(AuditHash.entryHash(PREV, "p1", "approve", "human", "u1", "denied",
        "2026-11-16T12:00:00Z")).isNotEqualTo(base);
    assertThat(AuditHash.entryHash("b".repeat(64), "p1", "approve", "human", "u1", "ok",
        "2026-11-16T12:00:00Z")).isNotEqualTo(base);
  }

  @Test
  void fingerprintIsStableForTheSamePayload() {
    String payload = "{\"title\":\"需求\"}";
    assertThat(AuditHash.fingerprint(payload)).isEqualTo(AuditHash.fingerprint(payload)).hasSize(64);
    assertThat(AuditHash.fingerprint(payload)).isNotEqualTo(AuditHash.fingerprint(payload + " "));
  }
}
