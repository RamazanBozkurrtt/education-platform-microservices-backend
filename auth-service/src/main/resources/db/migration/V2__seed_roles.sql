INSERT INTO roles (name, created_at, updated_at)
VALUES
    ('ROLE_USER', NOW(), NOW()),
    ('ROLE_ADMIN', NOW(), NOW())
    ON CONFLICT (name) DO NOTHING;