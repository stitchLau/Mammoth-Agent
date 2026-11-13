package com.mammoth.domain.admin;

import java.util.List;

/** Ordered business tables for export/import (ADR-0008). Order satisfies SQLite foreign keys. */
final class AdminTables {

  static final String LOCKED_PASSWORD = "!mammoth-import-locked";

  static final List<String> TABLES = List.of(
      "organization",
      "app_user",
      "role_assignment",
      "customer",
      "product",
      "project",
      "project_product",
      "project_member",
      "artifact",
      "knowledge_document",
      "knowledge_source_ref",
      "knowledge_chunk",
      "audit_log",
      "work_item",
      "requirement_evidence",
      "requirement_assessment",
      "requirement_snapshot",
      "solution_version",
      "solution_design_item",
      "acceptance_criterion",
      "solution_task",
      "approval_record",
      "project_repository",
      "project_baseline",
      "project_baseline_repo",
      "execution_record",
      "execution_change",
      "execution_task",
      "execution_deviation",
      "delivery_snapshot",
      "knowledge_update_proposal");

  private AdminTables() {
  }
}
