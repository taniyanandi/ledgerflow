package com.ledgerflow.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerflow.domain.Payment;
import com.ledgerflow.fraud.RiskDecision;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the pure prompt-building/response-parsing logic — the parts with
 * real bug risk. The actual HTTP call to Anthropic is not exercised here (that
 * would require live network access or an API key); the try/catch fallback wrapping
 * it in explain() mirrors HttpFraudScoringClient's established fail-soft pattern.
 */
class AnthropicRiskExplanationClientTest {

    private final Payment payment = new Payment(UUID.randomUUID(), "ord-1", 250000, "INR");
    private final RiskDecision decision = new RiskDecision(0.87, RiskDecision.Decision.DECLINE,
            List.of(new RiskDecision.Reason("device_new", "unrecognized device", 0.32)), false);

    @Test
    void promptCitesTheRealPaymentAndReasonsVerbatim() {
        String prompt = AnthropicRiskExplanationClient.buildPrompt(payment, decision);

        assertThat(prompt).contains(payment.getId().toString());
        assertThat(prompt).contains("2500.00 INR");
        assertThat(prompt).contains("device_new");
        assertThat(prompt).contains("unrecognized device");
        assertThat(prompt).contains("DECLINE");
    }

    @Test
    void actionMatchesDecisionVerdict() {
        assertThat(AnthropicRiskExplanationClient.actionFor(decision)).contains("manual fraud review");
    }

    @Test
    void extractsTextFromTheFirstTextContentBlock() throws Exception {
        String json = """
                {"content":[{"type":"text","text":"Declined due to an unrecognized device."}]}
                """;
        var response = new ObjectMapper().readValue(json, AnthropicRiskExplanationClient.MessagesResponse.class);

        assertThat(AnthropicRiskExplanationClient.extractText(response))
                .isEqualTo("Declined due to an unrecognized device.");
    }

    @Test
    void returnsNullWhenResponseHasNoContent() {
        assertThat(AnthropicRiskExplanationClient.extractText(
                new AnthropicRiskExplanationClient.MessagesResponse(List.of()))).isNull();
        assertThat(AnthropicRiskExplanationClient.extractText(null)).isNull();
    }

    @Test
    void requestBodySerializesMaxTokensInSnakeCase() throws Exception {
        var request = new AnthropicRiskExplanationClient.MessagesRequest(
                "claude-test-model", 600, "system prompt",
                List.of(new AnthropicRiskExplanationClient.Message("user", "hello")));

        String json = new ObjectMapper().writeValueAsString(request);

        assertThat(json).contains("\"max_tokens\":600");
        assertThat(json).contains("\"model\":\"claude-test-model\"");
    }
}
