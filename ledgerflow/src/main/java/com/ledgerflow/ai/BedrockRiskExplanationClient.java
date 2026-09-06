package com.ledgerflow.ai;

import com.ledgerflow.domain.Payment;
import com.ledgerflow.fraud.RiskDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Calls Claude on AWS Bedrock to produce a natural-language risk narrative, strictly
 * grounded in the SHAP reasons already computed by the fraud model (the prompt
 * forbids inventing new risk factors). Runs only on-demand, outside the payment
 * saga — see RiskExplanationService. Any failure (timeout, throttling, auth,
 * malformed response) is caught and degrades to the same deterministic template
 * TemplateRiskExplanationClient uses, so a Bedrock outage can never break this
 * endpoint, only make its narrative less rich.
 */
@Component
@ConditionalOnProperty(name = "ledgerflow.ai.provider", havingValue = "bedrock")
public class BedrockRiskExplanationClient implements RiskExplanationClient {

    private static final Logger log = LoggerFactory.getLogger(BedrockRiskExplanationClient.class);

    private static final String SYSTEM_PROMPT = """
            You are a payments risk analyst writing a short note for a human reviewer.
            You will be given a fraud decision, a probability, and a list of SHAP feature
            contributions that the model already computed. Use ONLY those facts.
            Do not invent new risk factors or numbers not given to you.
            Write 2-4 plain-English sentences: what happened, why it matters, and a
            clear recommended next action for the reviewer.
            """;

    private final ChatClient chatClient;
    private final String modelId;

    public BedrockRiskExplanationClient(ChatClient.Builder chatClientBuilder,
                                        @Value("${ledgerflow.ai.bedrock.model-id}") String modelId) {
        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
        this.modelId = modelId;
    }

    @Override
    public RiskExplanation explain(Payment payment, RiskDecision decision) {
        try {
            String userPrompt = buildPrompt(payment, decision);
            String narrative = chatClient.prompt()
                    .user(userPrompt)
                    .call()
                    .content();
            if (narrative == null || narrative.isBlank()) {
                return degrade(decision);
            }
            String action = switch (decision.decision()) {
                case DECLINE -> "block and escalate to manual fraud review";
                case REVIEW -> "hold for manual review before capture";
                case APPROVE -> "no action needed; proceed as approved";
            };
            return new RiskExplanation(narrative.trim(), action, false, modelId);
        } catch (Exception ex) {
            log.warn("Bedrock risk explanation unavailable ({}); using template fallback", ex.toString());
            return degrade(decision);
        }
    }

    private RiskExplanation degrade(RiskDecision decision) {
        RiskExplanation fallback = TemplateRiskExplanationClient.template(decision);
        return new RiskExplanation(fallback.narrative(), fallback.recommendedAction(), true, null);
    }

    private String buildPrompt(Payment payment, RiskDecision decision) {
        StringBuilder reasons = new StringBuilder();
        for (RiskDecision.Reason r : decision.reasons()) {
            reasons.append(String.format("- %s: %s (contribution %.3f)%n",
                    r.feature(), r.explanation(), r.contribution()));
        }
        return String.format("""
                Payment: %s, amount %.2f %s.
                Fraud probability: %.3f
                Decision: %s
                Degraded (rules-based fallback, not the ML model): %s
                SHAP feature contributions:
                %s
                """,
                payment.getId(), payment.getAmountMinor() / 100.0, payment.getCurrency(),
                decision.fraudProbability(), decision.decision(), decision.degraded(), reasons);
    }
}
