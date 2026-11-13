-- M4 S3: read-only external import (ADR-0011). External records are evidence candidates, not
-- authoritative work items; mapping to a work item/evidence is an explicit human action.
CREATE TABLE external_source (
  id                 TEXT PRIMARY KEY,
  project_id         TEXT NOT NULL REFERENCES project(id),
  system_type        TEXT NOT NULL,
  name               TEXT NOT NULL,
  base_url_sanitized TEXT,
  status             TEXT NOT NULL,
  created_by         TEXT REFERENCES app_user(id),
  created_at         TEXT NOT NULL,
  UNIQUE (project_id, name)
);

CREATE TABLE import_batch (
  id               TEXT PRIMARY KEY,
  project_id       TEXT NOT NULL REFERENCES project(id),
  source_id        TEXT NOT NULL REFERENCES external_source(id),
  artifact_id      TEXT NOT NULL REFERENCES artifact(id),
  file_sha256      TEXT NOT NULL,
  mapping_profile  TEXT NOT NULL,
  status           TEXT NOT NULL,
  row_count        INTEGER NOT NULL DEFAULT 0,
  accepted_count   INTEGER NOT NULL DEFAULT 0,
  rejected_count   INTEGER NOT NULL DEFAULT 0,
  error_summary    TEXT,
  created_by       TEXT REFERENCES app_user(id),
  created_at       TEXT NOT NULL,
  finished_at      TEXT,
  UNIQUE (source_id, file_sha256)
);

CREATE TABLE external_record (
  id                    TEXT PRIMARY KEY,
  batch_id              TEXT NOT NULL REFERENCES import_batch(id),
  source_id             TEXT NOT NULL REFERENCES external_source(id),
  project_id            TEXT NOT NULL REFERENCES project(id),
  external_type         TEXT NOT NULL,
  external_id           TEXT NOT NULL,
  external_key          TEXT,
  external_version       TEXT NOT NULL,
  title                 TEXT NOT NULL,
  description           TEXT,
  source_url_sanitized  TEXT,
  content_hash          TEXT NOT NULL,
  mapped_work_item_id   TEXT REFERENCES work_item(id),
  mapped_evidence_id    TEXT REFERENCES requirement_evidence(id),
  mapped_criterion_id   TEXT,
  status                TEXT NOT NULL,
  note                  TEXT,
  metadata_json         TEXT,
  created_at            TEXT NOT NULL,
  UNIQUE (source_id, external_type, external_id, external_version)
);

CREATE INDEX idx_external_source_project ON external_source(project_id);
CREATE INDEX idx_import_batch_project ON import_batch(project_id, created_at);
CREATE INDEX idx_external_record_project_status ON external_record(project_id, status);
CREATE INDEX idx_external_record_work_item ON external_record(mapped_work_item_id);
CREATE INDEX idx_external_record_criterion ON external_record(mapped_criterion_id);
