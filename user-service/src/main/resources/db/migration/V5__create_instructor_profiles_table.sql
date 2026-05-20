CREATE TABLE IF NOT EXISTS instructor_profiles (
    instructor_profile_id BIGSERIAL PRIMARY KEY,
    auth_user_id BIGINT NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    biography VARCHAR(2000) NOT NULL,
    expertise JSONB NOT NULL,
    profile_image_url VARCHAR(500),
    website_url VARCHAR(500),
    linkedin_url VARCHAR(500),
    github_url VARCHAR(500),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT
);

CREATE INDEX IF NOT EXISTS idx_instructor_profiles_auth_user_id
    ON instructor_profiles (auth_user_id);
