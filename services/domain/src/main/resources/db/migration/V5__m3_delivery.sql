-- M3 delivery loop schema (SQLite dialect; business columns stay PostgreSQL portable).
-- See docs/specs/M3-PHASE3-PLAN.md §9 and ADR-0007/0008.

CREATE TABLE project_repository (
  id            TEXT PRIMARY KEY,
  project_id    TEXT NOT NULL REFERENCES project(id),
  label         TEXT NOT NULL,
  absolute_path TEXT NOT NULL,
  remote_url    TEXT,
  default_branch TEXT,
  status        TEXT NOT NULL,
  added_by      TEXT REFERENCES app_user(id),
  added_at      TEXT NOT NULL,
  UNIQUE (project_id, absolute_path)
);

CREATE INDEX idx_project_repository_project ON project_repository(project_id, status);

CREATE TABLE project_baseline (
  id           TEXT PRIMARY KEY,
  project_id   TEXT NOT NULL REFERENCES project(id),
  note         TEXT,
  complete     INTEGER NOT NULL,
  captured_by  TEXT REFERENCES app_user(id),
  captured_at  TEXT NOT NULL
);

CREATE INDEX idx_project_baseline_project ON project_baseline(project_id, captured_at);

CREATE TABLE project_baseline_repo (
  id             TEXT PRIMARY KEY,
  baseline_id    TEXT NOT NULL REFERENCES project_baseline(id),
  repository_id  TEXT NOT NULL REFERENCES project_repository(id),
  commit_sha     TEXT NOT NULL,
  branch         TEXT,
  dirty          INTEGER NOT NULL,
  detached       INTEGER NOT NULL,
  worktree       INTEGER NOT NULL,
  captured_at    TEXT NOT NULL
);

CREATE INDEX idx_project_baseline_repo_baseline ON project_baseline_repo(baseline_id);

CREATE TABLE execution_record (
  id                  TEXT PRIMARY KEY,
  project_id          TEXT NOT NULL REFERENCES project(id),
  work_item_id        TEXT NOT NULL REFERENCES work_item(id),
  solution_version_id TEXT NOT NULL REFERENCES solution_version(id),
  baseline_id         TEXT NOT NULL REFERENCES project_baseline(id),
  repository_id       TEXT NOT NULL REFERENCES project_repository(id),
  from_commit         TEXT NOT NULL,
  to_commit           TEXT NOT NULL,
  diverged            INTEGER NOT NULL,
  summary             TEXT,
  tests_note          TEXT,
  recorded_by         TEXT REFERENCES app_user(id),
  recorded_at         TEXT NOT NULL
);

CREATE INDEX idx_execution_record_work_item ON execution_record(work_item_id, recorded_at);

CREATE TABLE execution_change (
  id                  TEXT PRIMARY KEY,
  execution_record_id TEXT NOT NULL REFERENCES execution_record(id),
  file_path           TEXT NOT NULL,
  change_type         TEXT NOT NULL,
  old_path            TEXT,
  additions           INTEGER NOT NULL,
  deletions           INTEGER NOT NULL
);

CREATE INDEX idx_execution_change_record ON execution_change(execution_record_id);

CREATE TABLE execution_task (
  id                  TEXT PRIMARY KEY,
  execution_record_id TEXT NOT NULL REFERENCES execution_record(id),
  task_id             TEXT NOT NULL REFERENCES solution_task(id),
  status              TEXT NOT NULL,
  note                TEXT
);

CREATE INDEX idx_execution_task_record ON execution_task(execution_record_id);
CREATE INDEX idx_execution_task_task ON execution_task(task_id);

CREATE TABLE execution_deviation (
  id                  TEXT PRIMARY KEY,
  execution_record_id TEXT NOT NULL REFERENCES execution_record(id),
  deviation_type      TEXT NOT NULL,
  target_type         TEXT,
  target_id           TEXT,
  description         TEXT NOT NULL,
  created_by          TEXT REFERENCES app_user(id),
  created_at          TEXT NOT NULL
);

CREATE INDEX idx_execution_deviation_record ON execution_deviation(execution_record_id);

CREATE TABLE delivery_snapshot (
  id                  TEXT PRIMARY KEY,
  project_id          TEXT NOT NULL REFERENCES project(id),
  work_item_id        TEXT NOT NULL REFERENCES work_item(id),
  solution_version_id TEXT NOT NULL REFERENCES solution_version(id),
  baseline_id         TEXT,
  title               TEXT NOT NULL,
  body_markdown       TEXT NOT NULL,
  status              TEXT NOT NULL,
  artifact_id         TEXT REFERENCES artifact(id),
  created_by          TEXT REFERENCES app_user(id),
  created_at          TEXT NOT NULL,
  approved_by         TEXT REFERENCES app_user(id),
  approved_at         TEXT
);

CREATE INDEX idx_delivery_snapshot_work_item ON delivery_snapshot(work_item_id, status);

CREATE TABLE knowledge_update_proposal (
  id                    TEXT PRIMARY KEY,
  project_id            TEXT NOT NULL REFERENCES project(id),
  work_item_id          TEXT NOT NULL REFERENCES work_item(id),
  delivery_snapshot_id  TEXT NOT NULL REFERENCES delivery_snapshot(id),
  title                 TEXT NOT NULL,
  body_markdown         TEXT NOT NULL,
  status                TEXT NOT NULL,
  knowledge_document_id TEXT REFERENCES knowledge_document(id),
  created_by            TEXT REFERENCES app_user(id),
  created_at            TEXT NOT NULL,
  decided_by            TEXT REFERENCES app_user(id),
  decided_at            TEXT
);

CREATE INDEX idx_knowledge_update_proposal_work_item
  ON knowledge_update_proposal(work_item_id, status);

ALTER TABLE knowledge_document ADD COLUMN applicable_baseline_id TEXT;
