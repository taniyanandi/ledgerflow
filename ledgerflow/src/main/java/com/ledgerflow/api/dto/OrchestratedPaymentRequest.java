package com.ledgerflow.api.dto;

import com.ledgerflow.fraud.RiskSignals;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Request for the orchestrated flow. Risk signals are optional — when omitted,
 * sensible defaults are used — because in production they are derived server-side.
 */
public record OrchestratedPaymentRequest(
        @NotBlank String merchantReference,
        @Positive long amountMinor,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        RiskInput risk
) {
    public record RiskInput(
            Double amountZscore,
            Integer txnVelocity1h,
            Double geoDistanceKm,
            Integer deviceNew,
            Integer hourOfDay,
            Integer isForeign,
            Integer cardAgeDays
    ) {}

    public RiskSignals toSignals() {
        double amountMajor = amountMinor / 100.0;
        if (risk == null) {
            return RiskSignals.defaults(amountMajor);
        }
        return new RiskSignals(
                amountMajor,
                risk.amountZscore() != null ? risk.amountZscore() : 0.0,
                risk.txnVelocity1h() != null ? risk.txnVelocity1h() : 1,
                risk.geoDistanceKm() != null ? risk.geoDistanceKm() : 5.0,
                risk.deviceNew() != null ? risk.deviceNew() : 0,
                risk.hourOfDay() != null ? risk.hourOfDay() : 12,
                risk.isForeign() != null ? risk.isForeign() : 0,
                risk.cardAgeDays() != null ? risk.cardAgeDays() : 365);
    }
}
