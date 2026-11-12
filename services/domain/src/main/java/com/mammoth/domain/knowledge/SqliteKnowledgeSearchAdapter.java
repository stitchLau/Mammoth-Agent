package com.mammoth.domain.knowledge;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * SQLite FTS5 search over knowledge chunks (ADR-0005).
 *
 * <p>FTS5 trigram matches contiguous substrings, so a natural-language question is converted into an
 * OR query over the question's character trigrams and ranked by BM25 — the approach validated in
 * docs/spikes/S7-RETRIEVAL-SPIKE.md. Only {@code verified} documents in the requested project are
 * returned; scope and lifecycle filtering happen inside the query.
 */
@Component
public class SqliteKnowledgeSearchAdapter implements KnowledgeSearchAdapter {

  private final JdbcTemplate jdbcTemplate;

  public SqliteKnowledgeSearchAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public List<Hit> search(String projectId, String query, int topK) {
    String match = trigramQuery(query);
    if (match.isEmpty()) {
      return List.of();
    }
    String sql = """
        SELECT chunk.knowledge_document_id, chunk.id, chunk.ordinal, chunk.text,
               bm25(knowledge_chunk_fts) AS rank
        FROM knowledge_chunk_fts
        JOIN knowledge_chunk chunk ON chunk.rowid = knowledge_chunk_fts.rowid
        JOIN knowledge_document document ON document.id = chunk.knowledge_document_id
        WHERE knowledge_chunk_fts MATCH ?
          AND document.status = 'verified'
          AND document.scope_type = 'project'
          AND document.scope_id = ?
        ORDER BY rank
        LIMIT ?
        """;
    return jdbcTemplate.query(
        sql,
        (resultSet, rowIndex) -> new Hit(
            resultSet.getString(1),
            resultSet.getString(2),
            resultSet.getInt(3),
            resultSet.getString(4),
            resultSet.getDouble(5)),
        match,
        projectId,
        Math.max(1, Math.min(topK, 50)));
  }

  @Override
  public long indexedChunkCount() {
    Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM knowledge_chunk", Long.class);
    return count == null ? 0 : count;
  }

  /** Character trigrams OR-joined, quoted for FTS5 phrase matching; empty for queries under 3 chars. */
  static String trigramQuery(String query) {
    if (query == null) {
      return "";
    }
    String normalized = query.trim();
    if (normalized.isEmpty()) {
      return "";
    }
    Set<String> trigrams = new LinkedHashSet<>();
    for (int index = 0; index + 3 <= normalized.length(); index++) {
      trigrams.add(normalized.substring(index, index + 3));
    }
    if (trigrams.isEmpty()) {
      return "";
    }
    return String.join(" OR ", trigrams.stream().map(term -> "\"" + term + "\"").toList());
  }
}
