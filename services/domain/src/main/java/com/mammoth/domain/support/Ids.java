package com.mammoth.domain.support;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Small shared helpers for application-generated identifiers and ISO-8601 timestamps. */
public final class Ids {

  private Ids() {
  }

  /** Stable, sortable-enough identifier; app-generated so no database sequences are required. */
  public static String newId() {
    return UUID.randomUUID().toString();
  }

  /** Timestamps are stored as ISO-8601 TEXT to stay portable across SQLite and PostgreSQL. */
  public static String nowIso() {
    return Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
  }
}
