package com.mammoth.domain.solution;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A development task of a solution version. It must trace to a design item or directly to an
 * acceptance criterion (docs/03 §2) — enforced by {@link SolutionConsistency}.
 */
@Entity
@Table(name = "solution_task")
public class SolutionTask {

  @Id
  private String id;

  private String solutionVersionId;

  private String designItemId;

  private String acceptanceCriterionId;

  private String title;

  private String description;

  private String doneWhen;

  private String dependsOnTaskId;

  private int ordinal;

  protected SolutionTask() {
  }

  public SolutionTask(String id, String solutionVersionId, String designItemId,
      String acceptanceCriterionId, String title, String description, String doneWhen,
      String dependsOnTaskId, int ordinal) {
    this.id = id;
    this.solutionVersionId = solutionVersionId;
    this.designItemId = designItemId;
    this.acceptanceCriterionId = acceptanceCriterionId;
    this.title = title;
    this.description = description;
    this.doneWhen = doneWhen;
    this.dependsOnTaskId = dependsOnTaskId;
    this.ordinal = ordinal;
  }

  public void update(String designItemId, String acceptanceCriterionId, String title,
      String description, String doneWhen, String dependsOnTaskId) {
    this.designItemId = designItemId;
    this.acceptanceCriterionId = acceptanceCriterionId;
    this.title = title;
    this.description = description;
    this.doneWhen = doneWhen;
    this.dependsOnTaskId = dependsOnTaskId;
  }

  public String getId() {
    return id;
  }

  public String getSolutionVersionId() {
    return solutionVersionId;
  }

  public String getDesignItemId() {
    return designItemId;
  }

  public String getAcceptanceCriterionId() {
    return acceptanceCriterionId;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public String getDoneWhen() {
    return doneWhen;
  }

  public String getDependsOnTaskId() {
    return dependsOnTaskId;
  }

  public int getOrdinal() {
    return ordinal;
  }
}
