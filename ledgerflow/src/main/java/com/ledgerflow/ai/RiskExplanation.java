package com.ledgerflow.ai;

/** A natural-language narrative generated over an already-computed {@code RiskDecision}. */
public record RiskExplanation(
        String narrative,
        String recommendedAction,
        boolean degraded,   // true when produced by the template fallback, not a real LLM call
        String modelId      // null for the template path
) {}
