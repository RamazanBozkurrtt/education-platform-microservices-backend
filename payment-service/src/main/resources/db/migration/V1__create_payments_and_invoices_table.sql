CREATE TABLE IF NOT EXISTS payments (
    payment_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    course_id VARCHAR(64) NOT NULL,
    course_title_snapshot VARCHAR(255) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    provider VARCHAR(30) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    provider_payment_id VARCHAR(64) NOT NULL UNIQUE,
    idempotency_key VARCHAR(64),
    paid_at TIMESTAMP,
    refunded_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT
);

CREATE TABLE IF NOT EXISTS invoices (
    invoice_id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL UNIQUE,
    invoice_number VARCHAR(40) NOT NULL UNIQUE,
    invoice_date TIMESTAMP NOT NULL,
    buyer_full_name VARCHAR(120),
    buyer_email VARCHAR(160),
    buyer_tax_number VARCHAR(30),
    buyer_address VARCHAR(500),
    course_title_snapshot VARCHAR(255) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT
);

CREATE INDEX IF NOT EXISTS idx_payments_user_id ON payments (user_id);
CREATE INDEX IF NOT EXISTS idx_payments_course_id ON payments (course_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments (status);
CREATE UNIQUE INDEX IF NOT EXISTS idx_payments_provider_payment_id ON payments (provider_payment_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_payments_idempotency
    ON payments (user_id, course_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_invoices_payment_id ON invoices (payment_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_invoices_invoice_number ON invoices (invoice_number);
