CREATE TABLE IF NOT EXISTS instructor_projection (
    instructor_id VARCHAR(64) PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    profile_image_url VARCHAR(500),
    headline VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    source_updated_at TIMESTAMPTZ NOT NULL,
    last_event_id VARCHAR(64) NOT NULL,
    last_event_version BIGINT NOT NULL,
    projection_updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_instructor_projection_status
    ON instructor_projection (status);

CREATE TABLE IF NOT EXISTS processed_kafka_event (
    event_id VARCHAR(64) PRIMARY KEY,
    topic VARCHAR(200) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);
