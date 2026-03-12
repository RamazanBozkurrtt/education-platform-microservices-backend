ALTER TABLE profiles
    ADD COLUMN IF NOT EXISTS auth_user_id BIGINT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_profiles_auth_user_id
    ON profiles (auth_user_id)
    WHERE auth_user_id IS NOT NULL;
