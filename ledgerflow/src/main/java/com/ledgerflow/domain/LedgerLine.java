package com.ledgerflow.domain;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * One posting to one account. Amounts are stored as integer minor units
 * (e.g. paise / cents) as a {@code long} — never as floating point — so money
 * arithmetic is always exact.
 */
@Entity
@Table(name = "ledger_lines")
public class LedgerLine {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntry journalEntry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private LedgerAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Enums.Direction direction;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    protected LedgerLine() {}

    public LedgerLine(UUID id, LedgerAccount account, Enums.Direction direction,
                      long amountMinor, String currency) {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("Ledger line amount must be positive");
        }
        this.id = id;
        this.account = account;
        this.direction = direction;
        this.amountMinor = amountMinor;
        this.currency = currency;
    }

    public UUID getId() { return id; }
    public JournalEntry getJournalEntry() { return journalEntry; }
    public void setJournalEntry(JournalEntry journalEntry) { this.journalEntry = journalEntry; }
    public LedgerAccount getAccount() { return account; }
    public Enums.Direction getDirection() { return direction; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
}
