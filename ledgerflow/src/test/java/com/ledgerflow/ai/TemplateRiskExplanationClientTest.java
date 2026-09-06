package com.ledgerflow.ai;

import com.ledgerflow.domain.Payment;
import com.ledgerflow.fraud.RiskDecision;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateRiskExplanationClientTest {

    private final TemplateRiskExplanationClient client = new TemplateRiskExplanationClient();

    @Test
    void narrativeCitesTopReasonsAndRecommendsEscalation() {
        Payment payment = new Payment(UUID.randomUUID(), "ord-1", 500000, "INR");
        RiskDecision decision = new RiskDecision(0.91, RiskDecision.Decision.DECLINE,
                List.of(new RiskDecision.Reason("device_new", "unrecognized device", 0.32),
                        new RiskDecision.Reason("geo_distance_km", "far from usual location", 0.21)),
                false);

        RiskExplanation explanation = client.explain(payment, decision);

        assertThat(explanation.narrative()).contains("91%");
        assertThat(explanation.narrative()).contains("device_new");
        assertThat(explanation.narrative()).contains("geo_distance_km");
        assertThat(explanation.recommendedAction()).contains("manual fraud review");
        assertThat(explanation.modelId()).isNull();
    }

    @Test
    void flagsDegradedDecisionsInTheNarrative() {
        Payment payment = new Payment(UUID.randomUUID(), "ord-2", 1000, "INR");
        RiskDecision decision = new RiskDecision(0.5, RiskDecision.Decision.REVIEW,
                List.of(new RiskDecision.Reason("fallback", "rules-based fallback (3 risk flags)", 3)),
                true);

        RiskExplanation explanation = client.explain(payment, decision);

        assertThat(explanation.narrative()).contains("degraded");
        assertThat(explanation.recommendedAction()).contains("hold for manual review");
    }

    @Test
    void handlesEmptyReasonsGracefully() {
        Payment payment = new Payment(UUID.randomUUID(), "ord-3", 1000, "INR");
        RiskDecision decision = new RiskDecision(0.05, RiskDecision.Decision.APPROVE, List.of(), false);

        RiskExplanation explanation = client.explain(payment, decision);

        assertThat(explanation.narrative()).contains("no individual risk factors were reported");
        assertThat(explanation.recommendedAction()).contains("no action needed");
    }
}
