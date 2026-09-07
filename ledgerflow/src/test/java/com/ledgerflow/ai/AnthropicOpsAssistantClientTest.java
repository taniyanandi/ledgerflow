package com.ledgerflow.ai;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnthropicOpsAssistantClientTest {

    @Test
    void promptGroundsTheModelInTheRealRetrievedFacts() {
        UUID id = UUID.randomUUID();
        OpsAssistantTool.PaymentSummary summary = new OpsAssistantTool.PaymentSummary(
                id, "ord-1", 250000, "INR", "FAILED", "2026-01-01T00:00:00Z");
        OpsAssistantTool.PaymentDetail detail = new OpsAssistantTool.PaymentDetail(
                summary, 0.87, "DECLINE", "[]", "Declined due to high risk.", 3);

        String prompt = AnthropicOpsAssistantClient.buildPrompt("why was this declined?", detail);

        assertThat(prompt).contains("why was this declined?");
        assertThat(prompt).contains(id.toString());
        assertThat(prompt).contains("FAILED");
        assertThat(prompt).contains("2500.00 INR");
        assertThat(prompt).contains("DECLINE");
        assertThat(prompt).contains("Declined due to high risk.");
    }

    @Test
    void promptHandlesAPaymentWithNoRiskDataYet() {
        UUID id = UUID.randomUUID();
        OpsAssistantTool.PaymentSummary summary = new OpsAssistantTool.PaymentSummary(
                id, "ord-2", 1000, "INR", "CAPTURED", "2026-01-01T00:00:00Z");
        OpsAssistantTool.PaymentDetail detail = new OpsAssistantTool.PaymentDetail(
                summary, null, null, null, null, 0);

        String prompt = AnthropicOpsAssistantClient.buildPrompt("what's the status?", detail);

        assertThat(prompt).contains("none recorded");
        assertThat(prompt).contains("none generated yet");
    }
}
