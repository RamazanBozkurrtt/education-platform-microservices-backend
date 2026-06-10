CREATE INDEX IF NOT EXISTS idx_user_roles_role_id
    ON user_roles (role_id);

CREATE INDEX IF NOT EXISTS idx_refresh_token_user_id_revoked_false
    ON refresh_token (user_id)
    WHERE revoked = false;
