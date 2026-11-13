-- M4 S5: in-app notifications, external delivery outbox and durable server jobs (ADR-0013).
CREATE TABLE notification (
  id                 TEXT PRIMARY KEY,
  org_id             TEXT NOT NULL,
  recipient_user_id  TEXT NOT NULL REFERENCES app_user(id),
  type               TEXT NOT NULL,
  title              TEXT NOT NULL,
  body               TEXT,
  project_id         TEXT,
  object_type        TEXT,
  object_id          TEXT,
  link               TEXT,
  severity           TEXT NOT NULL,
  status             TEXT NOT NULL,
  dedupe_key         TEXT NOT NULL,
  created_at         TEXT NOT NULL,
  read_at            TEXT,
  UNIQUE (recipient_user_id, dedupe_key)
);

CREATE TABLE notification_delivery (
  id               TEXT PRIMARY KEY,
  notification_id  TEXT NOT NULL REFERENCES notification(id),
  channel          TEXT NOT NULL,
  status           TEXT NOT NULL,
  attempts         INTEGER NOT NULL DEFAULT 0,
  last_error       TEXT,
  next_attempt_at  TEXT,
  sent_at          TEXT,
  created_at       TEXT NOT NULL,
  updated_at       TEXT NOT NULL
);

CREATE TABLE server_job (
  id               TEXT PRIMARY KEY,
  type             TEXT NOT NULL,
  payload_json     TEXT,
  status           TEXT NOT NULL,
  attempts         INTEGER NOT NULL DEFAULT 0,
  next_run_at      TEXT,
  locked_by        TEXT,
  locked_at        TEXT,
  idempotency_key  TEXT NOT NULL UNIQUE,
  created_by       TEXT,
  created_at       TEXT NOT NULL,
  updated_at       TEXT NOT NULL,
  last_error       TEXT
);

CREATE INDEX idx_notification_recipient_status ON notification(recipient_user_id, status, created_at);
CREATE INDEX idx_notification_project ON notification(project_id, created_at);
CREATE INDEX idx_notification_delivery_status ON notification_delivery(status, next_attempt_at);
CREATE INDEX idx_server_job_status_next ON server_job(status, next_run_at);
