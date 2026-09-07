package com.ledgerflow.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ledgerflow.domain.Payment;
import com.ledgerflow.fraud.RiskDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * Calls Claude (Anthropic API directly, no cloud provider in between) to produce a
 * natural-language risk narrative, strictly grounded in the SHAP reasons already
 * computed by the fraud model — the prompt forbids inventing new risk factors.
 * Runs only on-demand, outside the payment saga (see RiskExplanationService). Any
 * failure — timeout, auth, malformed response — is caught and degrades to the same
 * deterministic template TemplateRiskExplanationClient uses, so an Anthropic outage
 * can never break this endpoint, only make its narrative less rich.
 */
@Component
@ConditionalOnProperty(name = "ledgerflow.ai.provider", havingValue = "anthropic")
public class AnthropicRiskExplanationClient implements RiskExplanationClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicRiskExplanationClient.class);
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private static final String SYSTEM_PROMPT = """
            You are a payments risk analyst writing a short note for a human reviewer.
            You will be given a fraud decision, a probability, and a list of SHAP feature
            contributions that the model already computed. Use ONLY those facts.
            Do not invent new risk factors or numbers not given to you.
            Write 2-4 plain-English sentences: what happened, why it matters, and a
            clear recommended next action for the reviewer.
            """;

    private final RestClient client;
    private final String model;
    private final int maxTokens;

    public AnthropicRiskExplanationClient(RestClient.Builder builder,
                                          @Value("${ledgerflow.ai.anthropic.api-key}") String apiKey,
                                          @Value("${ledgerflow.ai.anthropic.model}") String model,
                                          @Value("${ledgerflow.ai.anthropic.timeout-ms}") int timeoutMs,
                                          @Value("${ledgerflow.ai.anthropic.max-tokens}") int maxTokens) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        factory.setReadTimeout(timeoutMs);
        this.client = builder.baseUrl(API_URL)
                .requestFactory(factory)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                .build();
        this.model = model;
        this.maxTokens = maxTokens;
    }

    @Override
    public RiskExplanation explain(Payment payment, RiskDecision decision) {
        try {
            MessagesResponse response = client.post()
                    .body(new MessagesRequest(model, maxTokens, SYSTEM_PROMPT,
                            List.of(new Message("user", buildPrompt(payment, decision)))))
                    .retrieve()
                    .body(MessagesResponse.class);

            String narrative = extractText(response);
            if (narrative == null || narrative.isBlank()) {
                return degrade(decision);
            }
            String action = actionFor(decision);
            return new RiskExplanation(narrative.trim(), action, false, model);
        } catch (Exception ex) {
            log.warn("Anthropic risk explanation unavailable ({}); using template fallback", ex.toString());
            return degrade(decision);
        }
    }

    static String extractText(MessagesResponse response) {
        if (response == null || response.content() == null) {
            return null;
        }
        return response.content().stream()
                .filter(b -> "text".equals(b.type()))
                .map(ContentBlock::text)
                .findFirst().orElse(null);
    }

    static String actionFor(RiskDecision decision) {
        return switch (decision.decision()) {
            case DECLINE -> "block and escalate to manual fraud review";
            case REVIEW -> "hold for manual review before capture";
            case APPROVE -> "no action needed; proceed as approved";
        };
    }

    static String buildPrompt(Payment payment, RiskDecision decision) {
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

    private RiskExplanation degrade(RiskDecision decision) {
        RiskExplanation fallback = TemplateRiskExplanationClient.template(decision);
        return new RiskExplanation(fallback.narrative(), fallback.recommendedAction(), true, null);
    }

    // --- Anthropic Messages API wire DTOs ---

    record MessagesRequest(String model, @JsonProperty("max_tokens") int maxTokens,
                           String system, List<Message> messages) {}

    record Message(String role, String content) {}

    record MessagesResponse(List<ContentBlock> content) {}

    record ContentBlock(String type, String text) {}
}
