-- ADR-0015 P1 (V11): agent-controlled write proposals, human approvals and append-only write audit.
-- Business columns stay PostgreSQL portable; JSON payloads are validated by the application layer.

CREATE TABLE agent_proposal (
  id                TEXT PRIMARY KEY,
  project_id        TEXT NOT NULL REFERENCES project(id),
  proposal_type     TEXT NOT NULL,
  payload_json      TEXT NOT NULL,
  summary           TEXT,
  proposer_kind     TEXT NOT NULL,
  proposer_user_id  TEXT REFERENCES app_user(id),
  agent_run_ref     TEXT,
  idempotency_key   TEXT NOT NULL,
  status            TEXT NOT NULL,
  risk_level        TEXT NOT NULL,
  failure_reason    TEXT,
  created_at        TEXT NOT NULL,
  decided_at        TEXT,
  applied_at        TEXT,
  UNIQUE (idempotency_key)
);

CREATE INDEX idx_agent_proposal_project_status ON agent_proposal(project_id, status);
CREATE INDEX idx_agent_proposal_proposer ON agent_proposal(proposer_user_id);
CREATE INDEX idx_agent_proposal_created_at ON agent_proposal(created_at);

CREATE TABLE agent_proposal_approval (
  id                TEXT PRIMARY KEY,
  proposal_id       TEXT NOT NULL REFERENCES agent_proposal(id),
  approver_user_id  TEXT NOT NULL REFERENCES app_user(id),
  decision          TEXT NOT NULL,
  reason            TEXT,
  reviewer_role     TEXT NOT NULL,
  human_session_ref TEXT,
  decided_at        TEXT NOT NULL,
  UNIQUE (proposal_id, reviewer_role)
);

CREATE INDEX idx_agent_proposal_approval_proposal ON agent_proposal_approval(proposal_id);

-- Append-only audit for propose/approve/reject/apply/deny. proposal_id intentionally has no FK:
-- denials must be recorded even when no proposal row exists (fail-closed evidence).
CREATE TABLE agent_write_audit (
  id                  TEXT PRIMARY KEY,
  proposal_id         TEXT NOT NULL,
  stage               TEXT NOT NULL,
  actor_kind          TEXT NOT NULL,
  actor_user_id       TEXT,
  outcome             TEXT NOT NULL,
  request_fingerprint TEXT,
  domain_object_id    TEXT,
  prev_hash           TEXT,
  entry_hash          TEXT,
  at                  TEXT NOT NULL
);

CREATE INDEX idx_agent_write_audit_proposal_at ON agent_write_audit(proposal_id, at);
CREATE INDEX idx_agent_write_audit_stage_at ON agent_write_audit(stage, at);
