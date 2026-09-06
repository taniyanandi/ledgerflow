package com.ledgerflow.domain;

public final class Enums {
    private Enums() {}

    /** Normal accounting classification. Assets increase on DEBIT; liabilities increase on CREDIT. */
    public enum AccountType { ASSET, LIABILITY, REVENUE, EXPENSE }

    /** The two sides of every ledger line. */
    public enum Direction { DEBIT, CREDIT }

    /** Lifecycle of a payment. Phase 1 covers the happy path plus refunds. */
    public enum PaymentStatus { INITIATED, AUTHORIZED, CAPTURED, FAILED, REFUNDED }
}
