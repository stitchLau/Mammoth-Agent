package com.mammoth.domain.knowledge;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Evidence reference: which artifact (and where in it) backs a knowledge claim. */
@Entity
@Table(name = "knowledge_source_ref")
public class KnowledgeSourceRef {

  @Id
  private String id;

  private String knowledgeDocumentId;

  private String artifactId;

  private String locator;

  private String note;

  protected KnowledgeSourceRef() {
  }

  public KnowledgeSourceRef(String id, String knowledgeDocumentId, String artifactId, String locator,
      String note) {
    this.id = id;
    this.knowledgeDocumentId = knowledgeDocumentId;
    this.artifactId = artifactId;
    this.locator = locator;
    this.note = note;
  }

  public String getId() {
    return id;
  }

  public String getKnowledgeDocumentId() {
    return knowledgeDocumentId;
  }

  public String getArtifactId() {
    return artifactId;
  }

  public String getLocator() {
    return locator;
  }

  public String getNote() {
    return note;
  }
}
