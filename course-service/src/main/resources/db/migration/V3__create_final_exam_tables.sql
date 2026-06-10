CREATE TABLE IF NOT EXISTS final_exam (
    id BIGSERIAL PRIMARY KEY,
    course_id VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    passing_score NUMERIC(5,2) NOT NULL,
    question_count INTEGER NOT NULL,
    duration_minutes INTEGER NOT NULL,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    availability_days INTEGER NOT NULL DEFAULT 3,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(64) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_final_exam_course_active_true
    ON final_exam (course_id)
    WHERE active = TRUE;

CREATE INDEX IF NOT EXISTS idx_final_exam_course
    ON final_exam (course_id);

CREATE TABLE IF NOT EXISTS exam_question (
    id BIGSERIAL PRIMARY KEY,
    final_exam_id BIGINT NOT NULL REFERENCES final_exam(id) ON DELETE CASCADE,
    question_text TEXT NOT NULL,
    image_url VARCHAR(1000) NULL,
    image_object_key VARCHAR(500) NULL,
    order_index INTEGER NOT NULL,
    points NUMERIC(8,2) NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(64) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_exam_question_final_exam
    ON exam_question (final_exam_id);

CREATE INDEX IF NOT EXISTS idx_exam_question_final_exam_active
    ON exam_question (final_exam_id, active);

CREATE TABLE IF NOT EXISTS exam_option (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL REFERENCES exam_question(id) ON DELETE CASCADE,
    option_text TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL,
    order_index INTEGER NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_exam_option_question
    ON exam_option (question_id);

CREATE TABLE IF NOT EXISTS exam_attempt (
    id BIGSERIAL PRIMARY KEY,
    final_exam_id BIGINT NOT NULL REFERENCES final_exam(id) ON DELETE RESTRICT,
    course_id VARCHAR(64) NOT NULL,
    student_id VARCHAR(64) NOT NULL,
    attempt_number INTEGER NOT NULL,
    attempt_status VARCHAR(32) NOT NULL,
    result_status VARCHAR(32) NOT NULL DEFAULT 'NONE',
    started_at TIMESTAMPTZ NOT NULL,
    submitted_at TIMESTAMPTZ NULL,
    terminated_at TIMESTAMPTZ NULL,
    expired_at TIMESTAMPTZ NULL,
    score NUMERIC(5,2) NULL,
    passed BOOLEAN NOT NULL DEFAULT FALSE,
    created_by VARCHAR(64) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_exam_attempt_student_exam_number UNIQUE (student_id, final_exam_id, attempt_number)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_exam_attempt_active_per_student
    ON exam_attempt (final_exam_id, student_id)
    WHERE attempt_status = 'IN_PROGRESS';

CREATE INDEX IF NOT EXISTS idx_exam_attempt_exam_student
    ON exam_attempt (final_exam_id, student_id);

CREATE INDEX IF NOT EXISTS idx_exam_attempt_course_student
    ON exam_attempt (course_id, student_id);

CREATE TABLE IF NOT EXISTS exam_attempt_answer (
    id BIGSERIAL PRIMARY KEY,
    attempt_id BIGINT NOT NULL REFERENCES exam_attempt(id) ON DELETE CASCADE,
    question_id BIGINT NOT NULL REFERENCES exam_question(id) ON DELETE RESTRICT,
    selected_option_id BIGINT NOT NULL REFERENCES exam_option(id) ON DELETE RESTRICT,
    answered_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_exam_attempt_answer_attempt_question UNIQUE (attempt_id, question_id)
);

CREATE INDEX IF NOT EXISTS idx_exam_attempt_answer_attempt
    ON exam_attempt_answer (attempt_id);

CREATE TABLE IF NOT EXISTS certificate_eligibility (
    id BIGSERIAL PRIMARY KEY,
    course_id VARCHAR(64) NOT NULL,
    student_id VARCHAR(64) NOT NULL,
    final_exam_id BIGINT NOT NULL REFERENCES final_exam(id) ON DELETE RESTRICT,
    attempt_id BIGINT NOT NULL REFERENCES exam_attempt(id) ON DELETE RESTRICT,
    eligible BOOLEAN NOT NULL DEFAULT FALSE,
    earned_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_certificate_eligibility_course_student UNIQUE (course_id, student_id)
);

CREATE INDEX IF NOT EXISTS idx_certificate_eligibility_student_course
    ON certificate_eligibility (student_id, course_id);
