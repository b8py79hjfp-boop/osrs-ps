-- The panel's own schema (panel brief §1). Game data is never stored here.
CREATE SCHEMA IF NOT EXISTS panel;

-- Brief §2: server-side sessions; the cookie carries only the random id (stored hashed).
CREATE TABLE panel.sessions (
    id_hash      bytea PRIMARY KEY,
    account_id   bigint NOT NULL,
    display_name text NOT NULL,
    rank         text NOT NULL,
    ip           text NOT NULL,
    user_agent   text NOT NULL DEFAULT '',
    created_at   timestamptz NOT NULL DEFAULT now(),
    last_seen_at timestamptz NOT NULL DEFAULT now(),
    rank_checked_at timestamptz NOT NULL DEFAULT now(),
    revoked_at   timestamptz
);
CREATE INDEX sessions_account ON panel.sessions (account_id);

-- Brief §2: second factors, encrypted at rest (the key is a panel secret, not in the database).
CREATE TABLE panel.second_factors (
    id          bigserial PRIMARY KEY,
    account_id  bigint NOT NULL,
    kind        text NOT NULL CHECK (kind IN ('totp', 'webauthn')),
    label       text NOT NULL DEFAULT '',
    secret      bytea NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    last_used_at timestamptz
);
CREATE INDEX second_factors_account ON panel.second_factors (account_id);

-- Brief §4 Settings: runtime-safe settings as key/value.
CREATE TABLE panel.settings (
    key        text PRIMARY KEY,
    value      jsonb NOT NULL,
    updated_by text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- Brief §6: every control, deploy and backup step is a job row with its log.
CREATE TABLE panel.jobs (
    id          bigserial PRIMARY KEY,
    kind        text NOT NULL,
    actor       text NOT NULL,
    state       text NOT NULL CHECK (state IN ('queued', 'running', 'succeeded', 'failed', 'cancelled')),
    detail      jsonb NOT NULL DEFAULT '{}',
    log         text NOT NULL DEFAULT '',
    created_at  timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz
);
CREATE INDEX jobs_created ON panel.jobs (created_at DESC);
