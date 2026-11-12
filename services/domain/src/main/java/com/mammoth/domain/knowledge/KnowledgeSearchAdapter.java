package com.mammoth.domain.knowledge;

import java.util.List;

/**
 * Retrieval seam (ADR-0005): M1 ships a SQLite FTS5 implementation; a vector or hybrid adapter can
 * be added later without changing the domain service.
 */
public interface KnowledgeSearchAdapter {

  record Hit(String documentId, String chunkId, int ordinal, String text, double score) {
  }

  /** Search verified knowledge inside one project scope only (authorization is enforced upstream). */
  List<Hit> search(String projectId, String query, int topK);

  long indexedChunkCount();
}
