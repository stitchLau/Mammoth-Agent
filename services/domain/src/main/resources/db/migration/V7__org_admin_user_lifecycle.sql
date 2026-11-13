-- M4 S1: organization admin, user lifecycle and forced password change (ADR-0006 addendum).
ALTER TABLE app_user ADD COLUMN email TEXT;
ALTER TABLE app_user ADD COLUMN account_type TEXT NOT NULL DEFAULT 'human';
ALTER TABLE app_user ADD COLUMN password_reset_required INTEGER NOT NULL DEFAULT 0;
ALTER TABLE session ADD COLUMN password_reset_required INTEGER NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX idx_role_assignment_unique
  ON role_assignment(user_id, role, scope_type, scope_id);

-- Backfill: the oldest active user becomes the first org_admin. If no active user exists, fall back
-- to the oldest user so an operator still has an administrative identity to repair the account.
INSERT INTO role_assignment (id, user_id, role, scope_type, scope_id, created_at)
SELECT 'ra-org-admin-' || id, id, 'org_admin', 'organization', org_id, created_at
FROM app_user
WHERE id = (
  SELECT id FROM app_user
  ORDER BY CASE WHEN status = 'active' THEN 0 ELSE 1 END, created_at, id
  LIMIT 1
)
AND NOT EXISTS (
  SELECT 1 FROM role_assignment
  WHERE role = 'org_admin' AND scope_type = 'organization' AND scope_id = app_user.org_id
);
