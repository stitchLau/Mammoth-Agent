-- Mammoth M1 baseline schema (SQLite dialect; PostgreSQL migration lives under db/migration-pg).
-- Business tables are deliberately portable; only types/timestamps differ per dialect (ADR-0003).

CREATE TABLE organization (
  id          TEXT PRIMARY KEY,
  name        TEXT NOT NULL,
  created_at  TEXT NOT NULL
);

CREATE TABLE app_user (
  id             TEXT PRIMARY KEY,
  org_id         TEXT NOT NULL REFERENCES organization(id),
  username       TEXT NOT NULL UNIQUE,
  display_name   TEXT NOT NULL,
  password_hash  TEXT NOT NULL,
  status         TEXT NOT NULL,
  created_at     TEXT NOT NULL
);

CREATE TABLE role_assignment (
  id          TEXT PRIMARY KEY,
  user_id     TEXT NOT NULL REFERENCES app_user(id),
  role        TEXT NOT NULL,
  scope_type  TEXT NOT NULL,
  scope_id    TEXT NOT NULL,
  created_at  TEXT NOT NULL
);

CREATE TABLE customer (
  id          TEXT PRIMARY KEY,
  org_id      TEXT NOT NULL REFERENCES organization(id),
  name        TEXT NOT NULL,
  status      TEXT NOT NULL,
  created_at  TEXT NOT NULL
);

CREATE TABLE product (
  id          TEXT PRIMARY KEY,
  org_id      TEXT NOT NULL REFERENCES organization(id),
  name        TEXT NOT NULL,
  status      TEXT NOT NULL,
  created_at  TEXT NOT NULL
);

CREATE TABLE project (
  id            TEXT PRIMARY KEY,
  org_id        TEXT NOT NULL REFERENCES organization(id),
  customer_id   TEXT NOT NULL REFERENCES customer(id),
  code          TEXT NOT NULL UNIQUE,
  name          TEXT NOT NULL,
  status        TEXT NOT NULL,
  owner_user_id TEXT REFERENCES app_user(id),
  created_at    TEXT NOT NULL
);

CREATE TABLE project_product (
  project_id  TEXT NOT NULL REFERENCES project(id),
  product_id  TEXT NOT NULL REFERENCES product(id),
  PRIMARY KEY (project_id, product_id)
);

CREATE TABLE project_member (
  project_id  TEXT NOT NULL REFERENCES project(id),
  user_id     TEXT NOT NULL REFERENCES app_user(id),
  role        TEXT NOT NULL,
  created_at  TEXT NOT NULL,
  PRIMARY KEY (project_id, user_id)
);

CREATE TABLE artifact (
  id             TEXT PRIMARY KEY,
  project_id     TEXT NOT NULL REFERENCES project(id),
  kind           TEXT NOT NULL,
  original_name  TEXT NOT NULL,
  content_hash   TEXT NOT NULL,
  storage_path   TEXT NOT NULL,
  size_bytes     INTEGER NOT NULL,
  mime           TEXT,
  source_note    TEXT,
  uploaded_by    TEXT REFERENCES app_user(id),
  created_at     TEXT NOT NULL,
  UNIQUE (project_id, content_hash)
);

CREATE TABLE knowledge_document (
  id          TEXT PRIMARY KEY,
  scope_type  TEXT NOT NULL,
  scope_id    TEXT NOT NULL,
  doc_type    TEXT NOT NULL,
  title       TEXT NOT NULL,
  body        TEXT NOT NULL,
  status      TEXT NOT NULL,
  version     INTEGER NOT NULL DEFAULT 1,
  created_by  TEXT REFERENCES app_user(id),
  reviewer    TEXT REFERENCES app_user(id),
  reviewed_at TEXT,
  created_at  TEXT NOT NULL,
  updated_at  TEXT NOT NULL
);

CREATE TABLE knowledge_source_ref (
  id                    TEXT PRIMARY KEY,
  knowledge_document_id TEXT NOT NULL REFERENCES knowledge_document(id),
  artifact_id           TEXT NOT NULL REFERENCES artifact(id),
  locator               TEXT,
  note                  TEXT
);

CREATE TABLE knowledge_chunk (
  id                    TEXT PRIMARY KEY,
  knowledge_document_id TEXT NOT NULL REFERENCES knowledge_document(id),
  ordinal               INTEGER NOT NULL,
  text                  TEXT NOT NULL,
  index_version         INTEGER NOT NULL DEFAULT 1,
  UNIQUE (knowledge_document_id, ordinal, index_version)
);

CREATE TABLE audit_log (
  id             TEXT PRIMARY KEY,
  actor_user_id  TEXT,
  action         TEXT NOT NULL,
  object_type    TEXT NOT NULL,
  object_id      TEXT,
  at             TEXT NOT NULL,
  detail_json    TEXT
);

CREATE TABLE session (
  token_hash  TEXT PRIMARY KEY,
  user_id     TEXT NOT NULL REFERENCES app_user(id),
  created_at  INTEGER NOT NULL,
  expires_at  INTEGER NOT NULL,
  revoked_at  INTEGER
);

CREATE INDEX idx_project_org ON project(org_id);
CREATE INDEX idx_artifact_project ON artifact(project_id);
CREATE INDEX idx_knowledge_document_scope ON knowledge_document(scope_type, scope_id);
CREATE INDEX idx_knowledge_source_ref_document ON knowledge_source_ref(knowledge_document_id);
CREATE INDEX idx_knowledge_chunk_document ON knowledge_chunk(knowledge_document_id);
CREATE INDEX idx_audit_log_at ON audit_log(at);
CREATE INDEX idx_session_expires ON session(expires_at);
