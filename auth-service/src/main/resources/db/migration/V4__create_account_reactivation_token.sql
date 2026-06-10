CREATE TABLE IF NOT EXISTS account_reactivation_token (
    id BIGINT PRIMARY KEY,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    used_at TIMESTAMPTZ,
    user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_account_reactivation_token_user_id
    ON account_reactivation_token(user_id);

CREATE INDEX IF NOT EXISTS idx_account_reactivation_token_expires_at
    ON account_reactivation_token(expires_at);
