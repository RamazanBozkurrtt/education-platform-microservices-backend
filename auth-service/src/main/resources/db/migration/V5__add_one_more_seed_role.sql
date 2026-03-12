INSERT INTO roles (name, created_at, updated_at)
VALUES
    ('ROLE_INSTRUCTOR', NOW(), NOW())
ON CONFLICT (name) DO NOTHING;