-- Sign-in (panel phase 3): the login name for the password re-check, and the per-session CSRF token.
ALTER TABLE panel.sessions ADD COLUMN username text NOT NULL DEFAULT '';
ALTER TABLE panel.sessions ADD COLUMN csrf text NOT NULL DEFAULT '';
CREATE INDEX sessions_live ON panel.sessions (created_at DESC) WHERE revoked_at IS NULL;
