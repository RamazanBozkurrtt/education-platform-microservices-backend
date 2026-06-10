CREATE TABLE IF NOT EXISTS recommendation_log (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    course_id VARCHAR(64) NOT NULL,
    context VARCHAR(32) NOT NULL,
    score NUMERIC(8,4) NULL,
    reason VARCHAR(1000) NULL,
    strategy VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_recommendation_log_user_context_created
    ON recommendation_log (user_id, context, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_recommendation_log_course_created
    ON recommendation_log (course_id, created_at DESC);
