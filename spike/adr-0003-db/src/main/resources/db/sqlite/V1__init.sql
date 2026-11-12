-- SQLite baseline schema (ADR-0003 spike).
CREATE TABLE project (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL
);

CREATE TABLE knowledge_document (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  project_id TEXT NOT NULL REFERENCES project(id),
  title TEXT NOT NULL,
  body TEXT NOT NULL,
  status TEXT NOT NULL
);

-- Standalone FTS5 table with trigram tokenizer (Chinese substring search).
CREATE VIRTUAL TABLE knowledge_fts USING fts5(text, tokenize='trigram');
