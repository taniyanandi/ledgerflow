-- Persists the quantitative risk decision computed by the saga's risk-check step
-- (probability, verdict, SHAP reasons) so it can be looked up later — e.g. by the
-- LLM risk analyst, which runs OUTSIDE the saga and must never block a payment.
CREATE TABLE risk_decisions (
    id                 UUID PRIMARY KEY,
    payment_id         UUID             NOT NULL REFERENCES payments(id),
    fraud_probability  DOUBLE PRECISION NOT NULL,
    decision           VARCHAR(16)      NOT NULL,
    reasons            TEXT             NOT NULL, -- JSON array of {feature, explanation, contribution}
    degraded           BOOLEAN          NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ      NOT NULL
);

CREATE INDEX idx_risk_decisions_payment ON risk_decisions (payment_id, created_at DESC);

-- A generated natural-language narrative over a risk_decisions row. Generation is
-- expensive (LLM latency/cost) and itself an audit artifact, so each generation is
-- persisted rather than recomputed on every read.
CREATE TABLE risk_explanations (
    id                  UUID PRIMARY KEY,
    payment_id          UUID        NOT NULL REFERENCES payments(id),
    narrative           TEXT        NOT NULL,
    recommended_action  VARCHAR(64) NOT NULL,
    model_id            VARCHAR(128),
    degraded            BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_risk_explanations_payment ON risk_explanations (payment_id, created_at DESC);
