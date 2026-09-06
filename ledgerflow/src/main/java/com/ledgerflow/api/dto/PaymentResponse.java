package com.ledgerflow.api.dto;

import com.ledgerflow.domain.Payment;

import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        String merchantReference,
        long amountMinor,
        String currency,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getId(), p.getMerchantReference(), p.getAmountMinor(),
                p.getCurrency(), p.getStatus().name(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
