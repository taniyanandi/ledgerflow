package com.ledgerflow.domain;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * A ledger account (e.g. ACQUIRER_CASH, MERCHANT_PAYABLE). Balances are never
 * stored on this row; they are always derived by summing ledger lines. This is
 * a deliberate correctness choice: a stored balance can drift, a derived one cannot.
 */
@Entity
@Table(name = "ledger_accounts")
public class LedgerAccount {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Enums.AccountType type;

    @Column(nullable = false, length = 3)
    private String currency;

    protected LedgerAccount() {}

    public LedgerAccount(UUID id, String code, String name, Enums.AccountType type, String currency) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.type = type;
        this.currency = currency;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public Enums.AccountType getType() { return type; }
    public String getCurrency() { return currency; }
}
