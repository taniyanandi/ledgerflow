package com.ledgerflow.fraud;

/**
 * The engineered features a fraud decision is made on. In production these are
 * derived server-side from the customer's history (velocity, z-scores, geo). Here
 * they arrive on the request so the flow is demonstrable end-to-end; the contract
 * with the model is identical either way.
 */
public record RiskSignals(
        double amount,
        double amountZscore,
        int txnVelocity1h,
        double geoDistanceKm,
        int deviceNew,
        int hourOfDay,
        int isForeign,
        int cardAgeDays
) {
    public static RiskSignals defaults(double amount) {
        return new RiskSignals(amount, 0.0, 1, 5.0, 0, 12, 0, 365);
    }
}
