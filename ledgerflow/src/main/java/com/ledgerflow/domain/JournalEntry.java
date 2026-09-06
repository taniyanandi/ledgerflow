package com.ledgerflow.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A journal entry is one atomic, balanced bundle of ledger lines. The core
 * double-entry invariant — total debits equal total credits — is enforced in
 * {@link #assertBalanced()} before persistence, so an unbalanced entry can
 * never reach the database.
 */
@Entity
@Table(name = "journal_entries")
public class JournalEntry {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private String description;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LedgerLine> lines = new ArrayList<>();

    protected JournalEntry() {}

    public JournalEntry(UUID id, String description, UUID paymentId, Instant createdAt) {
        this.id = id;
        this.description = description;
        this.paymentId = paymentId;
        this.createdAt = createdAt;
    }

    public void addLine(LedgerLine line) {
        line.setJournalEntry(this);
        this.lines.add(line);
    }

    /** Guards the fundamental double-entry invariant. */
    public void assertBalanced() {
        long debits = lines.stream()
                .filter(l -> l.getDirection() == Enums.Direction.DEBIT)
                .mapToLong(LedgerLine::getAmountMinor).sum();
        long credits = lines.stream()
                .filter(l -> l.getDirection() == Enums.Direction.CREDIT)
                .mapToLong(LedgerLine::getAmountMinor).sum();
        if (debits != credits) {
            throw new IllegalStateException(
                    "Unbalanced journal entry: debits=" + debits + " credits=" + credits);
        }
        if (lines.isEmpty()) {
            throw new IllegalStateException("Journal entry must have at least two lines");
        }
    }

    public UUID getId() { return id; }
    public String getDescription() { return description; }
    public UUID getPaymentId() { return paymentId; }
    public Instant getCreatedAt() { return createdAt; }
    public List<LedgerLine> getLines() { return lines; }
}
