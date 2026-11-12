package com.mammoth.domain.workitem;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Readiness assessment for one dimension of a requirement (M2 plan §4.3). Rows are upserted; the
 * assessment is a human judgement, not an AI verdict (docs/07 §4).
 */
@Entity
@Table(name = "requirement_assessment")
public class RequirementAssessment {

  @Id
  private String id;

  private String workItemId;

  private String dimension;

  private String state;

  private String note;

  private String assumptionNote;

  private String decisionOwner;

  private String updatedBy;

  private String updatedAt;

  protected RequirementAssessment() {
  }

  public RequirementAssessment(String id, String workItemId, String dimension) {
    this.id = id;
    this.workItemId = workItemId;
    this.dimension = dimension;
  }

  public void update(String state, String note, String assumptionNote, String decisionOwner,
      String actorUserId, String now) {
    RequirementDimension.requireValidState(state);
    this.state = state;
    this.note = blankToNull(note);
    this.assumptionNote = blankToNull(assumptionNote);
    this.decisionOwner = blankToNull(decisionOwner);
    this.updatedBy = actorUserId;
    this.updatedAt = now;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public String getId() {
    return id;
  }

  public String getWorkItemId() {
    return workItemId;
  }

  public String getDimension() {
    return dimension;
  }

  public String getState() {
    return state;
  }

  public String getNote() {
    return note;
  }

  public String getAssumptionNote() {
    return assumptionNote;
  }

  public String getDecisionOwner() {
    return decisionOwner;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }
}
