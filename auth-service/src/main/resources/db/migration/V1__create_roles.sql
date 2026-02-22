CREATE TABLE IF NOT EXISTS roles (
                                    id BIGSERIAL PRIMARY KEY,
                                    name VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );

-- Seed için kritik
CREATE UNIQUE INDEX IF NOT EXISTS ux_role_name ON role(name);