
package com.ledgerflow.api.dto;

import com.ledgerflow.domain.WebhookDelivery;

import java.time.Instant;
import java.util.UUID;

public record WebhookDeliveryResponse(
        UUID id,
        UUID eventId,
        String eventType,
        String status,
        int attemptCount,
        Integer httpStatusCode,
        Instant lastAttemptAt
) {
    public static WebhookDeliveryResponse from(WebhookDelivery delivery) {
        return new WebhookDeliveryResponse(
                delivery.getId(),
                delivery.getEventId(),
                delivery.getEventType(),
                delivery.getStatus().name(),
                delivery.getAttemptCount(),
                delivery.getLastHttpStatus(),
                delivery.getLastAttemptAt()
        );
    }
}
