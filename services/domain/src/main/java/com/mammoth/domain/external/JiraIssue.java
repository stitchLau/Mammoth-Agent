package com.mammoth.domain.external;

/** Normalized Jira issue; no attachments, comments or changelog are read in M4.5-1. */
public record JiraIssue(String key, String summary, String description, String status,
    String issueType, String priority, String created, String updated, String assignee,
    String reporter, String component, String projectKey, String url) {
}
