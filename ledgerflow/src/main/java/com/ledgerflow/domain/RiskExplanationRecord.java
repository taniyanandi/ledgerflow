package com.ledgerflow.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A generated natural-language narrative over a {@link RiskDecisionRecord}. Each
 * generation is persisted (never overwritten) — LLM calls are costly/slow, and the
 * narrative is itself an audit artifact, the same discipline payment_events uses.
 */
@Entity
@Table(name = "risk_explanations")
public class RiskExplanationRecord {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(nullable = false, columnDefinition = "text")
    private String narrative;

    @Column(name = "recommended_action", nullable = false, length = 64)
    private String recommendedAction;

    @Column(name = "model_id", length = 128)
    private String modelId;

    @Column(nullable = false)
    private boolean degraded;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RiskExplanationRecord() {}

    public RiskExplanationRecord(UUID id, UUID paymentId, String narrative,
                                 String recommendedAction, String modelId,
                                 boolean degraded, Instant createdAt) {
        this.id = id;
        this.paymentId = paymentId;
        this.narrative = narrative;
        this.recommendedAction = recommendedAction;
        this.modelId = modelId;
        this.degraded = degraded;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public String getNarrative() { return narrative; }
    public String getRecommendedAction() { return recommendedAction; }
    public String getModelId() { return modelId; }
    public boolean isDegraded() { return degraded; }
    public Instant getCreatedAt() { return createdAt; }
}
