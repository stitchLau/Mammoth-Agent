-- ADR-0005 / M1 T8: full-text index over knowledge chunks.
-- External-content FTS5 keeps the index derived from knowledge_chunk and maintained by triggers,
-- so chunks can always be rebuilt from documents without losing the source of truth.

CREATE VIRTUAL TABLE knowledge_chunk_fts USING fts5(
  text,
  content='knowledge_chunk',
  content_rowid='rowid',
  tokenize='trigram'
);

CREATE TRIGGER knowledge_chunk_fts_ai AFTER INSERT ON knowledge_chunk BEGIN
  INSERT INTO knowledge_chunk_fts(rowid, text) VALUES (new.rowid, new.text);
END;

CREATE TRIGGER knowledge_chunk_fts_ad AFTER DELETE ON knowledge_chunk BEGIN
  INSERT INTO knowledge_chunk_fts(knowledge_chunk_fts, rowid, text)
  VALUES ('delete', old.rowid, old.text);
END;

CREATE TRIGGER knowledge_chunk_fts_au AFTER UPDATE ON knowledge_chunk BEGIN
  INSERT INTO knowledge_chunk_fts(knowledge_chunk_fts, rowid, text)
  VALUES ('delete', old.rowid, old.text);
  INSERT INTO knowledge_chunk_fts(rowid, text) VALUES (new.rowid, new.text);
END;

CREATE INDEX idx_knowledge_document_status ON knowledge_document(status);
