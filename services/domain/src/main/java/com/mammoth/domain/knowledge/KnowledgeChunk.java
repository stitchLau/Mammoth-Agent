package com.mammoth.domain.knowledge;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Derived search chunk (ADR-0005: derived data, rebuildable, never the source of truth). */
@Entity
@Table(name = "knowledge_chunk")
public class KnowledgeChunk {

  @Id
  private String id;

  private String knowledgeDocumentId;

  private int ordinal;

  private String text;

  private int indexVersion;

  protected KnowledgeChunk() {
  }

  public KnowledgeChunk(String id, String knowledgeDocumentId, int ordinal, String text) {
    this.id = id;
    this.knowledgeDocumentId = knowledgeDocumentId;
    this.ordinal = ordinal;
    this.text = text;
    this.indexVersion = 1;
  }

  public String getId() {
    return id;
  }

  public String getKnowledgeDocumentId() {
    return knowledgeDocumentId;
  }

  public int getOrdinal() {
    return ordinal;
  }

  public String getText() {
    return text;
  }

  public int getIndexVersion() {
    return indexVersion;
  }
}
