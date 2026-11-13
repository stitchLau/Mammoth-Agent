package com.mammoth.domain.external;

import java.util.List;

/** Parser output and preview report; preview returns a small sample, never the whole file. */
public final class ImportReport {

  public record RowError(int row, String message) {
  }

  public record ParseResult(List<ImportedRecord> records, List<RowError> errors, int rowCount) {
  }

  public record Preview(int rowCount, int acceptedCount, int rejectedCount, List<RowError> errors,
      List<ImportedRecord> sample) {
  }

  private ImportReport() {
  }

  public static Preview preview(ParseResult result) {
    int sampleSize = Math.min(result.records().size(), 20);
    return new Preview(result.rowCount(), result.records().size(), result.errors().size(),
        result.errors(), result.records().subList(0, sampleSize));
  }
}
