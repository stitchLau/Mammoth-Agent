-- M2 requirement and solution schema (SQLite dialect; business columns stay PostgreSQL portable).
-- See docs/specs/M2-PHASE2-PLAN.md §4 for the aggregate boundaries and ADR-0010 for snapshots.

CREATE TABLE work_item (
  id                          TEXT PRIMARY KEY,
  project_id                  TEXT NOT NULL REFERENCES project(id),
  item_type                   TEXT NOT NULL,
  item_no                     TEXT NOT NULL,
  title                       TEXT NOT NULL,
  summary                     TEXT,
  status                      TEXT NOT NULL,
  priority                    TEXT NOT NULL,
  source_note                 TEXT,
  owner_user_id               TEXT REFERENCES app_user(id),
  created_by                  TEXT REFERENCES app_user(id),
  prior_status                TEXT,
  ready_confirmed_by          TEXT REFERENCES app_user(id),
  ready_confirmed_at          TEXT,
  -- Forward references (snapshot/solution) are application-managed to avoid a circular FK
  -- between work_item and requirement_snapshot/solution_version on PostgreSQL.
  context_snapshot_id         TEXT,
  approved_solution_version_id TEXT,
  created_at                  TEXT NOT NULL,
  updated_at                  TEXT NOT NULL,
  UNIQUE (project_id, item_no)
);

CREATE INDEX idx_work_item_project ON work_item(project_id, status);

CREATE TABLE requirement_evidence (
  id            TEXT PRIMARY KEY,
  work_item_id  TEXT NOT NULL REFERENCES work_item(id),
  batch_no      INTEGER NOT NULL,
  evidence_type TEXT NOT NULL,
  text          TEXT NOT NULL,
  artifact_id   TEXT REFERENCES artifact(id),
  locator       TEXT,
  captured_at   TEXT NOT NULL,
  added_by      TEXT REFERENCES app_user(id),
  created_at    TEXT NOT NULL,
  UNIQUE (work_item_id, batch_no)
);

CREATE INDEX idx_requirement_evidence_item ON requirement_evidence(work_item_id);

CREATE TABLE requirement_assessment (
  id              TEXT PRIMARY KEY,
  work_item_id    TEXT NOT NULL REFERENCES work_item(id),
  dimension       TEXT NOT NULL,
  state           TEXT NOT NULL,
  note            TEXT,
  assumption_note TEXT,
  decision_owner  TEXT,
  updated_by      TEXT REFERENCES app_user(id),
  updated_at      TEXT NOT NULL,
  UNIQUE (work_item_id, dimension)
);

CREATE TABLE requirement_snapshot (
  id           TEXT PRIMARY KEY,
  work_item_id TEXT NOT NULL REFERENCES work_item(id),
  version      INTEGER NOT NULL,
  payload_json TEXT NOT NULL,
  created_by   TEXT REFERENCES app_user(id),
  created_at   TEXT NOT NULL,
  UNIQUE (work_item_id, version)
);

CREATE TABLE solution_version (
  id                  TEXT PRIMARY KEY,
  work_item_id        TEXT NOT NULL REFERENCES work_item(id),
  version             INTEGER NOT NULL,
  status              TEXT NOT NULL,
  context_snapshot_id TEXT REFERENCES requirement_snapshot(id),
  title               TEXT NOT NULL,
  change_summary      TEXT,
  created_by          TEXT REFERENCES app_user(id),
  submitted_at        TEXT,
  approver_user_id    TEXT REFERENCES app_user(id),
  approved_at         TEXT,
  created_at          TEXT NOT NULL,
  updated_at          TEXT NOT NULL,
  UNIQUE (work_item_id, version)
);

CREATE INDEX idx_solution_version_item ON solution_version(work_item_id, status);

CREATE TABLE solution_design_item (
  id                  TEXT PRIMARY KEY,
  solution_version_id TEXT NOT NULL REFERENCES solution_version(id),
  item_kind           TEXT NOT NULL,
  parent_id           TEXT REFERENCES solution_design_item(id),
  title               TEXT NOT NULL,
  body                TEXT NOT NULL,
  ordinal             INTEGER NOT NULL
);

CREATE INDEX idx_solution_design_item_version ON solution_design_item(solution_version_id);

CREATE TABLE acceptance_criterion (
  id                  TEXT PRIMARY KEY,
  solution_version_id TEXT NOT NULL REFERENCES solution_version(id),
  design_item_id      TEXT NOT NULL REFERENCES solution_design_item(id),
  text                TEXT NOT NULL,
  ordinal             INTEGER NOT NULL
);

CREATE INDEX idx_acceptance_criterion_version ON acceptance_criterion(solution_version_id);

CREATE TABLE solution_task (
  id                      TEXT PRIMARY KEY,
  solution_version_id     TEXT NOT NULL REFERENCES solution_version(id),
  design_item_id          TEXT REFERENCES solution_design_item(id),
  acceptance_criterion_id TEXT REFERENCES acceptance_criterion(id),
  title                   TEXT NOT NULL,
  description             TEXT,
  done_when               TEXT,
  depends_on_task_id      TEXT REFERENCES solution_task(id),
  ordinal                 INTEGER NOT NULL
);

CREATE INDEX idx_solution_task_version ON solution_task(solution_version_id);

CREATE TABLE approval_record (
  id            TEXT PRIMARY KEY,
  object_type   TEXT NOT NULL,
  object_id     TEXT NOT NULL,
  action        TEXT NOT NULL,
  actor_user_id TEXT REFERENCES app_user(id),
  comment       TEXT,
  at            TEXT NOT NULL
);

CREATE INDEX idx_approval_record_object ON approval_record(object_type, object_id);
