-- M4 S2: MCP/agent tool-call audit ingested from the read-only MCP journal (ADR-0008 addendum).
CREATE TABLE agent_call (
  event_id       TEXT PRIMARY KEY,
  agent_user_id  TEXT NOT NULL,
  tool           TEXT NOT NULL,
  project_id     TEXT,
  result_count   INTEGER NOT NULL DEFAULT 0,
  duration_ms    INTEGER NOT NULL DEFAULT 0,
  error          TEXT,
  at             TEXT NOT NULL,
  ingested_at    TEXT NOT NULL,
  source_file    TEXT
);

CREATE INDEX idx_agent_call_project_at ON agent_call(project_id, at);
CREATE INDEX idx_agent_call_agent_at ON agent_call(agent_user_id, at);
