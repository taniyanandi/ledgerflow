package com.ledgerflow.fraud;

import java.util.List;

public record RiskDecision(
        double fraudProbability,
        Decision decision,
        List<Reason> reasons,
        boolean degraded          // true when produced by the fallback, not the model
) {
    public enum Decision { APPROVE, REVIEW, DECLINE }

    public record Reason(String feature, String explanation, double contribution) {}

    public boolean isBlocked() {
        return decision == Decision.DECLINE;
    }
}
