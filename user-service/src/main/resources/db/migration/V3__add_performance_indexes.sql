CREATE INDEX IF NOT EXISTS idx_profiles_email_lower
    ON profiles (lower(email));
