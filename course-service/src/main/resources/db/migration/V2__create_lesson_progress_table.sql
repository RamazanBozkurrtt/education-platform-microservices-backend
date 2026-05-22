CREATE TABLE IF NOT EXISTS lesson_progress (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    course_id VARCHAR(64) NOT NULL,
    lesson_id VARCHAR(64) NOT NULL,
    last_watched_second INTEGER NOT NULL DEFAULT 0,
    watched_percentage NUMERIC(5,2) NOT NULL DEFAULT 0,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_lesson_progress_user_course_lesson UNIQUE (user_id, course_id, lesson_id)
);

CREATE INDEX IF NOT EXISTS idx_lesson_progress_user_course
    ON lesson_progress (user_id, course_id);

CREATE INDEX IF NOT EXISTS idx_lesson_progress_course
    ON lesson_progress (course_id);

CREATE INDEX IF NOT EXISTS idx_lesson_progress_user_course_completed
    ON lesson_progress (user_id, course_id, completed);
