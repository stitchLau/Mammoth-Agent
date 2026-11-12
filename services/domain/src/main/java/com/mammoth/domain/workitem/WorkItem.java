package com.mammoth.domain.workitem;

import com.mammoth.domain.web.ApiException;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.Set;

/**
 * A requirement work item (docs/03 §1, docs/07 §1–2). M2 implements only the {@code requirement}
 * type; other work item types share the table but have no UI/flow yet.
 */
@Entity
@Table(name = "work_item")
public class WorkItem {

  public static final String TYPE_REQUIREMENT = "requirement";
  public static final Set<String> TYPES = Set.of(TYPE_REQUIREMENT);

  public static final String PRIORITY_LOW = "low";
  public static final String PRIORITY_NORMAL = "normal";
  public static final String PRIORITY_HIGH = "high";
  public static final String PRIORITY_CRITICAL = "critical";
  public static final Set<String> PRIORITIES =
      Set.of(PRIORITY_LOW, PRIORITY_NORMAL, PRIORITY_HIGH, PRIORITY_CRITICAL);

  @Id
  private String id;

  private String projectId;

  private String itemType;

  private String itemNo;

  private String title;

  private String summary;

  private String status;

  private String priority;

  private String sourceNote;

  private String ownerUserId;

  private String createdBy;

  private String priorStatus;

  private String readyConfirmedBy;

  private String readyConfirmedAt;

  private String contextSnapshotId;

  private String approvedSolutionVersionId;

  private String createdAt;

  private String updatedAt;

  protected WorkItem() {
  }

  public WorkItem(String id, String projectId, String itemNo, String title, String summary,
      String priority, String sourceNote, String ownerUserId, String createdBy, String now) {
    this.id = id;
    this.projectId = projectId;
    this.itemType = TYPE_REQUIREMENT;
    this.itemNo = itemNo;
    this.title = title;
    this.summary = summary;
    this.status = WorkItemStatus.DRAFT;
    this.priority = normalizePriority(priority);
    this.sourceNote = sourceNote;
    this.ownerUserId = ownerUserId == null || ownerUserId.isBlank() ? createdBy : ownerUserId;
    this.createdBy = createdBy;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public static String normalizePriority(String priority) {
    if (priority == null || priority.isBlank()) {
      return PRIORITY_NORMAL;
    }
    String normalized = priority.trim().toLowerCase(Locale.ROOT);
    if (!PRIORITIES.contains(normalized)) {
      throw ApiException.badRequest("unsupported priority: " + priority);
    }
    return normalized;
  }

  public void updateDetails(String title, String summary, String priority, String ownerUserId,
      String now) {
    if (WorkItemStatus.freezesContent(status)) {
      throw ApiException.conflict("requirement is " + status + " and can no longer be edited");
    }
    this.title = title;
    this.summary = summary;
    this.priority = normalizePriority(priority);
    if (ownerUserId != null && !ownerUserId.isBlank()) {
      this.ownerUserId = ownerUserId;
    }
    this.updatedAt = now;
  }

  /** Apply an already-validated transition (see {@link WorkItemStatus#requireTransition}). */
  public void applyTransition(String target, String now) {
    if (WorkItemStatus.isPaused(status) && !WorkItemStatus.WITHDRAWN.equals(target)
        && !WorkItemStatus.REJECTED.equals(target)) {
      this.priorStatus = null;
    } else if (WorkItemStatus.isPaused(target)) {
      this.priorStatus = this.status;
    }
    this.status = target;
    this.updatedAt = now;
  }

  public void confirmReadiness(String actorUserId, String snapshotId, String now) {
    this.readyConfirmedBy = actorUserId;
    this.readyConfirmedAt = now;
    this.contextSnapshotId = snapshotId;
    this.updatedAt = now;
  }

  public void attachApprovedSolution(String solutionVersionId) {
    this.approvedSolutionVersionId = solutionVersionId;
    this.updatedAt = com.mammoth.domain.support.Ids.nowIso();
  }

  public String getId() {
    return id;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getItemType() {
    return itemType;
  }

  public String getItemNo() {
    return itemNo;
  }

  public String getTitle() {
    return title;
  }

  public String getSummary() {
    return summary;
  }

  public String getStatus() {
    return status;
  }

  public String getPriority() {
    return priority;
  }

  public String getSourceNote() {
    return sourceNote;
  }

  public String getOwnerUserId() {
    return ownerUserId;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public String getPriorStatus() {
    return priorStatus;
  }

  public String getReadyConfirmedBy() {
    return readyConfirmedBy;
  }

  public String getReadyConfirmedAt() {
    return readyConfirmedAt;
  }

  public String getContextSnapshotId() {
    return contextSnapshotId;
  }

  public String getApprovedSolutionVersionId() {
    return approvedSolutionVersionId;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }
}
