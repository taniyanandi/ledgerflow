CREATE TABLE payment_events (
    event_id    UUID PRIMARY KEY,
    payment_id  UUID        NOT NULL,
    type        VARCHAR(64) NOT NULL,
    payload     TEXT        NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_payment_events_payment ON payment_events (payment_id, occurred_at);
