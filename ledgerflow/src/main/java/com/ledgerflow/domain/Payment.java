package com.ledgerflow.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "merchant_reference", nullable = false)
    private String merchantReference;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Enums.PaymentStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic lock: protects against concurrent state transitions on the same payment. */
    @Version
    private long version;

    protected Payment() {}

    public Payment(UUID id, String merchantReference, long amountMinor, String currency) {
        this.id = id;
        this.merchantReference = merchantReference;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.status = Enums.PaymentStatus.INITIATED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void transitionTo(Enums.PaymentStatus next) {
        this.status = next;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getMerchantReference() { return merchantReference; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public Enums.PaymentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
