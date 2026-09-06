CREATE TABLE payments (
    id                 UUID PRIMARY KEY,
    merchant_reference VARCHAR(255) NOT NULL,
    amount_minor       BIGINT       NOT NULL CHECK (amount_minor > 0),
    currency           CHAR(3)      NOT NULL,
    status             VARCHAR(16)  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_payments_merchant_reference ON payments (merchant_reference);
CREATE INDEX idx_payments_status ON payments (status);
