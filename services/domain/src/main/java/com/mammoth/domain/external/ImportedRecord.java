package com.mammoth.domain.external;

import java.util.Map;

/** One normalized external row; provenance/metadata are filled by the import service. */
public record ImportedRecord(String externalType, String externalId, String externalKey,
    String externalVersion, String title, String description, String status, String type,
    String priority, String createdAt, String updatedAt, String url, String assignee,
    String reporter, String component, Map<String, String> metadata) {
}
