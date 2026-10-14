package com.ledgerflow.api.dto;

import com.ledgerflow.validation.SupportedCurrency;
import jakarta.validation.constraints.*;

public record CreatePaymentRequest(
        @NotBlank String merchantReference,
        @Positive long amountMinor,
        @NotBlank @SupportedCurrency String currency
) {}
