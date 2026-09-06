package com.ledgerflow.api.dto;

import jakarta.validation.constraints.*;

public record CreatePaymentRequest(
        @NotBlank String merchantReference,
        @Positive long amountMinor,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "currency must be a 3-letter ISO code") String currency
) {}
