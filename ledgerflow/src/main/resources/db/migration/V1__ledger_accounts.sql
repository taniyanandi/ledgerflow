CREATE TABLE ledger_accounts (
    id       UUID PRIMARY KEY,
    code     VARCHAR(64)  NOT NULL UNIQUE,
    name     VARCHAR(255) NOT NULL,
    type     VARCHAR(16)  NOT NULL,
    currency CHAR(3)      NOT NULL
);

-- Chart of accounts for Phase 1. Real systems have one payable account per
-- merchant; a single shared account keeps the demo readable.
INSERT INTO ledger_accounts (id, code, name, type, currency) VALUES
    ('11111111-1111-1111-1111-111111111111', 'ACQUIRER_CASH',    'Acquirer settlement cash', 'ASSET',     'INR'),
    ('22222222-2222-2222-2222-222222222222', 'MERCHANT_PAYABLE', 'Merchant payable',         'LIABILITY', 'INR');
