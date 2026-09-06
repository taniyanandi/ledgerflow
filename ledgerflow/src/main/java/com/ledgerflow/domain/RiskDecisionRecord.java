package com.ledgerflow.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A durable record of the quantitative risk decision made by the saga's risk-check
 * step. Persisted at decision time (fast, local, no external call) so the LLM risk
 * analyst — which runs outside the saga — has something to explain later without
 * ever being on the payment's critical path.
 */
@Entity
@Table(name = "risk_decisions")
public class RiskDecisionRecord {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "fraud_probability", nullable = false)
    private double fraudProbability;

    @Column(nullable = false, length = 16)
    private String decision;

    /** JSON array of {feature, explanation, contribution}, mirroring RiskDecision.Reason. */
    @Column(nullable = false, columnDefinition = "text")
    private String reasons;

    @Column(nullable = false)
    private boolean degraded;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RiskDecisionRecord() {}

    public RiskDecisionRecord(UUID id, UUID paymentId, double fraudProbability,
                              String decision, String reasons, boolean degraded,
                              Instant createdAt) {
        this.id = id;
        this.paymentId = paymentId;
        this.fraudProbability = fraudProbability;
        this.decision = decision;
        this.reasons = reasons;
        this.degraded = degraded;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public double getFraudProbability() { return fraudProbability; }
    public String getDecision() { return decision; }
    public String getReasons() { return reasons; }
    public boolean isDegraded() { return degraded; }
    public Instant getCreatedAt() { return createdAt; }
}
