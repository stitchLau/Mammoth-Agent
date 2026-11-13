package com.mammoth.domain.external;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps canonical import fields to source columns/keys (ADR-0011). Profiles are data, not code; a
 * custom JSON profile can be supplied by the operator.
 */
public record ImportMappingProfile(String name, String defaultExternalType,
    Map<String, String> fields) {

  public static final List<String> CANONICAL_FIELDS = List.of(
      "externalId", "externalKey", "externalType", "title", "description", "status", "type",
      "priority", "createdAt", "updatedAt", "url", "assignee", "reporter", "component");

  public static ImportMappingProfile generic() {
    Map<String, String> fields = new LinkedHashMap<>();
    for (String field : CANONICAL_FIELDS) {
      fields.put(field, field);
    }
    return new ImportMappingProfile("generic", "record", fields);
  }

  /** Jira Data Center 8.x Chinese CSV export (headers verified against a desensitized sample). */
  public static ImportMappingProfile jira() {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("externalId", "关键字");
    fields.put("externalKey", "关键字");
    fields.put("externalType", null);
    fields.put("title", "概要");
    fields.put("description", "描述");
    fields.put("status", "状态");
    fields.put("type", "问题类型");
    fields.put("priority", "优先级");
    fields.put("createdAt", "创建日期");
    fields.put("updatedAt", "已更新");
    fields.put("url", "Issue URL");
    fields.put("assignee", "经办人");
    fields.put("reporter", "报告人");
    fields.put("component", "系统模块");
    return new ImportMappingProfile("jira", "issue", fields);
  }
}
