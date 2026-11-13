package com.mammoth.domain.execution;

import com.mammoth.domain.web.ApiException;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.Set;

/** Human classification of a deviation between Approved Design and what was implemented. */
@Entity
@Table(name = "execution_deviation")
public class ExecutionDeviation {

  public static final String CONFORM = "conform";
  public static final String EXTRA = "extra";
  public static final String MISSING = "missing";
  public static final String CHANGED = "changed";
  public static final Set<String> TYPES = Set.of(CONFORM, EXTRA, MISSING, CHANGED);

  public static final String TARGET_DESIGN_ITEM = "design_item";
  public static final String TARGET_TASK = "task";
  public static final String TARGET_CRITERION = "criterion";
  public static final Set<String> TARGET_TYPES =
      Set.of(TARGET_DESIGN_ITEM, TARGET_TASK, TARGET_CRITERION);

  @Id
  private String id;

  private String executionRecordId;

  private String deviationType;

  private String targetType;

  private String targetId;

  private String description;

  private String createdBy;

  private String createdAt;

  protected ExecutionDeviation() {
  }

  public ExecutionDeviation(String id, String executionRecordId, String deviationType,
      String targetType, String targetId, String description, String createdBy, String createdAt) {
    this.id = id;
    this.executionRecordId = executionRecordId;
    this.deviationType = normalizeType(deviationType);
    this.targetType = targetType == null || targetType.isBlank()
        ? null : normalizeTargetType(targetType);
    this.targetId = targetId == null || targetId.isBlank() ? null : targetId.trim();
    this.description = description;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
  }

  public static String normalizeType(String type) {
    if (type == null || type.isBlank()) {
      throw ApiException.badRequest("deviation type is required (conform/extra/missing/changed)");
    }
    String normalized = type.trim().toLowerCase(Locale.ROOT);
    if (!TYPES.contains(normalized)) {
      throw ApiException.badRequest("unsupported deviation type: " + type);
    }
    return normalized;
  }

  public static String normalizeTargetType(String type) {
    String normalized = type.trim().toLowerCase(Locale.ROOT);
    if (!TARGET_TYPES.contains(normalized)) {
      throw ApiException.badRequest("unsupported deviation target type: " + type);
    }
    return normalized;
  }

  public String getId() {
    return id;
  }

  public String getExecutionRecordId() {
    return executionRecordId;
  }

  public String getDeviationType() {
    return deviationType;
  }

  public String getTargetType() {
    return targetType;
  }

  public String getTargetId() {
    return targetId;
  }

  public String getDescription() {
    return description;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public String getCreatedAt() {
    return createdAt;
  }
}
