package com.ledgerflow.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A durable record of every published payment event. The Kafka consumer writes
 * these, giving an append-only audit trail that later phases (reconciliation,
 * webhooks) build on.
 */
@Entity
@Table(name = "payment_events")
public class PaymentEventRecord {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected PaymentEventRecord() {}

    public PaymentEventRecord(UUID eventId, UUID paymentId, String type,
                              String payload, Instant occurredAt) {
        this.eventId = eventId;
        this.paymentId = paymentId;
        this.type = type;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }

    public UUID getEventId() { return eventId; }
    public UUID getPaymentId() { return paymentId; }
    public String getType() { return type; }
    public String getPayload() { return payload; }
    public Instant getOccurredAt() { return occurredAt; }
}
