package com.ledgerflow.api.dto;

import com.ledgerflow.fraud.RiskDecision;

import java.util.List;

public record RiskDecisionResponse(
        double fraudProbability,
        String decision,
        List<RiskDecision.Reason> reasons,
        boolean degraded
) {
    public static RiskDecisionResponse from(RiskDecision decision) {
        return new RiskDecisionResponse(decision.fraudProbability(),
                decision.decision().name(), decision.reasons(), decision.degraded());
    }
}
