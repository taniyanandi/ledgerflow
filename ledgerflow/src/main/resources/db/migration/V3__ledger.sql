CREATE TABLE journal_entries (
    id          UUID PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    payment_id  UUID,
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE ledger_lines (
    id               UUID PRIMARY KEY,
    journal_entry_id UUID        NOT NULL REFERENCES journal_entries (id),
    account_id       UUID        NOT NULL REFERENCES ledger_accounts (id),
    direction        VARCHAR(8)  NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount_minor     BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency         CHAR(3)     NOT NULL
);

CREATE INDEX idx_ledger_lines_account ON ledger_lines (account_id);
CREATE INDEX idx_ledger_lines_journal ON ledger_lines (journal_entry_id);
