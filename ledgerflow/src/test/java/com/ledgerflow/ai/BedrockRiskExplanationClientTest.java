package com.ledgerflow.ai;

import com.ledgerflow.domain.Payment;
import com.ledgerflow.fraud.RiskDecision;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies BedrockRiskExplanationClient's grounding/fallback contract without ever
 * touching real AWS: the ChatClient is mocked so no network call happens.
 */
class BedrockRiskExplanationClientTest {

    private final Payment payment = new Payment(UUID.randomUUID(), "ord-1", 250000, "INR");
    private final RiskDecision decision = new RiskDecision(0.87, RiskDecision.Decision.DECLINE,
            List.of(new RiskDecision.Reason("device_new", "unrecognized device", 0.32)), false);

    @Test
    void returnsModelNarrativeOnSuccess() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class, RETURNS_DEEP_STUBS);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builder.defaultSystem(anyString()).build()).thenReturn(chatClient);
        when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn("Declined due to an unrecognized device. Recommend manual review.");

        BedrockRiskExplanationClient client = new BedrockRiskExplanationClient(builder, "claude-test-model");
        RiskExplanation explanation = client.explain(payment, decision);

        assertThat(explanation.degraded()).isFalse();
        assertThat(explanation.modelId()).isEqualTo("claude-test-model");
        assertThat(explanation.narrative()).contains("unrecognized device");
    }

    @Test
    void fallsBackToTemplateWhenTheModelCallFails() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class, RETURNS_DEEP_STUBS);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builder.defaultSystem(anyString()).build()).thenReturn(chatClient);
        when(chatClient.prompt().user(anyString()).call().content())
                .thenThrow(new RuntimeException("Bedrock throttled"));

        BedrockRiskExplanationClient client = new BedrockRiskExplanationClient(builder, "claude-test-model");
        RiskExplanation explanation = client.explain(payment, decision);

        assertThat(explanation.degraded()).isTrue();
        assertThat(explanation.modelId()).isNull();
        assertThat(explanation.narrative()).contains("device_new");
    }
}
