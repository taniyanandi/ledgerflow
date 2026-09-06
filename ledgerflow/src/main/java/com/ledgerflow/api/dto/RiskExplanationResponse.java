package com.ledgerflow.api.dto;

import com.ledgerflow.domain.RiskExplanationRecord;

public record RiskExplanationResponse(
        String narrative,
        String recommendedAction,
        boolean degraded,
        String modelId,
        String generatedAt
) {
    public static RiskExplanationResponse from(RiskExplanationRecord r) {
        return new RiskExplanationResponse(r.getNarrative(), r.getRecommendedAction(),
                r.isDegraded(), r.getModelId(), r.getCreatedAt().toString());
    }
}
