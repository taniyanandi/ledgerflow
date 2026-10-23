ALTER TABLE ledger_accounts
    ALTER COLUMN currency TYPE VARCHAR(3);

ALTER TABLE payments
    ALTER COLUMN currency TYPE VARCHAR(3);

ALTER TABLE ledger_lines
    ALTER COLUMN currency TYPE VARCHAR(3);

ALTER TABLE settlement_records
    ALTER COLUMN currency TYPE VARCHAR(3);

ALTER TABLE reconciliation_results
    ALTER COLUMN currency TYPE VARCHAR(3);
