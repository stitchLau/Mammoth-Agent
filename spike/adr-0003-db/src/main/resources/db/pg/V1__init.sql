-- PostgreSQL baseline schema (ADR-0003 spike).
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE project (
  id text PRIMARY KEY,
  name text NOT NULL
);

CREATE TABLE knowledge_document (
  id bigserial PRIMARY KEY,
  project_id text NOT NULL REFERENCES project(id),
  title text NOT NULL,
  body text NOT NULL,
  status text NOT NULL,
  body_tsv tsvector
);

CREATE INDEX idx_knowledge_document_project ON knowledge_document(project_id);
CREATE INDEX idx_knowledge_document_trgm ON knowledge_document USING gin (body gin_trgm_ops);
CREATE INDEX idx_knowledge_document_tsv ON knowledge_document USING gin (body_tsv);
