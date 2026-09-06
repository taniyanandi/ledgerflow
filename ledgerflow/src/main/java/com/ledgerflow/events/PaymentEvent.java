package com.ledgerflow.events;

import java.time.Instant;
import java.util.UUID;

/** An immutable fact about something that happened to a payment. */
public record PaymentEvent(
        UUID eventId,
        String type,          // PaymentAuthorized | PaymentCaptured | PaymentDeclined | PaymentRefunded
        UUID paymentId,
        long amountMinor,
        String currency,
        String status,
        Instant occurredAt,
        String detail
) {
    public static PaymentEvent of(String type, UUID paymentId, long amountMinor,
                                  String currency, String status, String detail) {
        return new PaymentEvent(UUID.randomUUID(), type, paymentId, amountMinor,
                currency, status, Instant.now(), detail);
    }
}
