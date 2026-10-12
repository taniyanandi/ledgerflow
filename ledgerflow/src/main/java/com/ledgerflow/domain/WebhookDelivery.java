package com.ledgerflow.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Tracks each attempt to deliver a webhook event to a subscriber. Failed
 * deliveries are retried with exponential backoff up to a maximum number of
 * attempts; after that they are dead-lettered.
 */
@Entity
@Table(name = "webhook_deliveries")
public class WebhookDelivery {

    public static final int MAX_ATTEMPTS = 5;

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private WebhookSubscription subscription;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    /** The JSON payload that was (or will be) sent. */
    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Enums.DeliveryStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    /** HTTP status code of the last delivery attempt, or null if none yet. */
    @Column(name = "last_http_status")
    private Integer lastHttpStatus;

    /** Error message from the last failed attempt. */
    @Column(name = "last_error")
    private String lastError;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    protected WebhookDelivery() {}

    public WebhookDelivery(WebhookSubscription subscription, UUID eventId,
                           String eventType, String payload) {
        this.id = UUID.randomUUID();
        this.subscription = subscription;
        this.eventId = eventId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = Enums.DeliveryStatus.PENDING;
        this.attemptCount = 0;
        this.createdAt = Instant.now();
        this.nextRetryAt = this.createdAt; // deliver immediately
    }

    public void recordSuccess(int httpStatus) {
        Instant now = Instant.now();
        this.attemptCount++;
        this.lastHttpStatus = httpStatus;
        this.status = Enums.DeliveryStatus.DELIVERED;
        this.deliveredAt = now;
        this.nextRetryAt = null;
        this.lastAttemptAt = now;
    }

    public void recordFailure(int httpStatus, String error) {
        this.attemptCount++;
        this.lastHttpStatus = httpStatus;
        this.lastError = error;
        this.lastAttemptAt = Instant.now();

        if (this.attemptCount >= MAX_ATTEMPTS) {
            this.status = Enums.DeliveryStatus.DEAD_LETTERED;
            this.nextRetryAt = null;
        } else {
            this.status = Enums.DeliveryStatus.FAILED;
            // Exponential backoff: 10s, 40s, 90s, 160s ...
            long backoffSeconds = 10L * (long) Math.pow(attemptCount, 2);
            this.nextRetryAt = Instant.now().plusSeconds(backoffSeconds);
        }
    }

    public void recordNetworkFailure(String error) {
        recordFailure(0, error);
    }

    public UUID getId() { return id; }
    public WebhookSubscription getSubscription() { return subscription; }
    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public Enums.DeliveryStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Integer getLastHttpStatus() { return lastHttpStatus; }
    public String getLastError() { return lastError; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }
}
