CREATE TABLE IF NOT EXISTS enrollments (
    enrollment_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    course_id VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT uk_enrollments_user_course UNIQUE (user_id, course_id)
);

CREATE INDEX IF NOT EXISTS idx_enrollments_user_id ON enrollments (user_id);
CREATE INDEX IF NOT EXISTS idx_enrollments_course_id ON enrollments (course_id);
CREATE INDEX IF NOT EXISTS idx_enrollments_status ON enrollments (status);
