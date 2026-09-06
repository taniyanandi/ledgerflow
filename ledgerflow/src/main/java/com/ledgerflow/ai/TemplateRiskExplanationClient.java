package com.ledgerflow.ai;

import com.ledgerflow.domain.Payment;
import com.ledgerflow.fraud.RiskDecision;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Deterministic, code-generated narrative built directly from the SHAP/rules reasons
 * already on the {@link RiskDecision} — no LLM call, no network, no AWS credentials
 * needed. This is the default provider (safe for local dev, tests, and CI) and also
 * what {@link BedrockRiskExplanationClient} falls back to on any failure, so the
 * fallback formatting logic exists in exactly one place.
 */
@Component
@ConditionalOnProperty(name = "ledgerflow.ai.provider", havingValue = "template", matchIfMissing = true)
public class TemplateRiskExplanationClient implements RiskExplanationClient {

    @Override
    public RiskExplanation explain(Payment payment, RiskDecision decision) {
        return template(decision);
    }

    static RiskExplanation template(RiskDecision decision) {
        String pct = String.format("%.0f%%", decision.fraudProbability() * 100);
        List<RiskDecision.Reason> top = decision.reasons().stream()
                .sorted(Comparator.comparingDouble(RiskDecision.Reason::contribution).reversed())
                .limit(3)
                .toList();

        String factors = top.isEmpty()
                ? "no individual risk factors were reported"
                : top.stream()
                    .map(r -> String.format("%s (%s, contribution %.2f)",
                            r.feature(), r.explanation(), r.contribution()))
                    .collect(Collectors.joining("; "));

        String action = switch (decision.decision()) {
            case DECLINE -> "block and escalate to manual fraud review";
            case REVIEW -> "hold for manual review before capture";
            case APPROVE -> "no action needed; proceed as approved";
        };

        String narrative = String.format(
                "Risk engine returned %s with an estimated fraud probability of %s%s. "
                        + "Top contributing factors: %s. Recommended action: %s.",
                decision.decision(), pct,
                decision.degraded() ? " (degraded: rules-based fallback, model service was unavailable)" : "",
                factors, action);

        return new RiskExplanation(narrative, action, true, null);
    }
}
