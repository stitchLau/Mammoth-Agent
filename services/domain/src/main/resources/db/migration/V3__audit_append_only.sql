-- M1 T7: the audit log is append-only at the database level.
-- Application code has no update/delete path; these triggers make accidental or malicious
-- mutations fail loudly instead of silently rewriting history.

CREATE TRIGGER audit_log_no_update BEFORE UPDATE ON audit_log
BEGIN
  SELECT RAISE(ABORT, 'audit_log is append-only');
END;

CREATE TRIGGER audit_log_no_delete BEFORE DELETE ON audit_log
BEGIN
  SELECT RAISE(ABORT, 'audit_log is append-only');
END;

CREATE INDEX idx_audit_actor ON audit_log(actor_user_id);
