package com.mammoth.domain.solution;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A verifiable acceptance criterion, always attached to a design item of the same version. */
@Entity
@Table(name = "acceptance_criterion")
public class AcceptanceCriterion {

  @Id
  private String id;

  private String solutionVersionId;

  private String designItemId;

  private String text;

  private int ordinal;

  protected AcceptanceCriterion() {
  }

  public AcceptanceCriterion(String id, String solutionVersionId, String designItemId, String text,
      int ordinal) {
    this.id = id;
    this.solutionVersionId = solutionVersionId;
    this.designItemId = designItemId;
    this.text = text;
    this.ordinal = ordinal;
  }

  public void update(String text) {
    this.text = text;
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

  public String getText() {
    return text;
  }

  public int getOrdinal() {
    return ordinal;
  }
}
