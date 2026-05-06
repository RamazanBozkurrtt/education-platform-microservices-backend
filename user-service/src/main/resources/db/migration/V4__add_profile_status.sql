ALTER TABLE profiles
    ADD COLUMN IF NOT EXISTS status VARCHAR(32);

UPDATE profiles
SET status = 'ACTUAL'
WHERE status IS NULL;

ALTER TABLE profiles
    ALTER COLUMN status SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_profiles_auth_user_id_status
    ON profiles (auth_user_id, status);
