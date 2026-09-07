package com.ledgerflow.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateOpsAssistantClientTest {

    @Mock
    OpsAssistantTool tool;

    @Test
    void answersDirectlyWhenQuestionContainsAKnownPaymentId() {
        UUID id = UUID.randomUUID();
        OpsAssistantTool.PaymentSummary summary = new OpsAssistantTool.PaymentSummary(
                id, "ord-1", 250000, "INR", "FAILED", "2026-01-01T00:00:00Z");
        OpsAssistantTool.PaymentDetail detail = new OpsAssistantTool.PaymentDetail(
                summary, 0.87, "DECLINE", "[]", "Declined due to high risk.", 3);
        when(tool.getPaymentDetail(id.toString())).thenReturn(detail);

        TemplateOpsAssistantClient client = new TemplateOpsAssistantClient(tool);
        OpsAssistantAnswer answer = client.ask("why was payment " + id + " declined");

        assertThat(answer.answer()).contains(id.toString());
        assertThat(answer.answer()).contains("FAILED");
        assertThat(answer.degraded()).isTrue();
        assertThat(answer.toolsUsed()).containsExactly("getPaymentDetail");
    }

    @Test
    void reportsUnknownPaymentIdClearly() {
        UUID id = UUID.randomUUID();
        when(tool.getPaymentDetail(id.toString())).thenReturn(null);

        TemplateOpsAssistantClient client = new TemplateOpsAssistantClient(tool);
        OpsAssistantAnswer answer = client.ask("status of " + id);

        assertThat(answer.answer()).contains("No payment found");
    }

    @Test
    void explainsLimitationWhenNoPaymentIdIsPresent() {
        TemplateOpsAssistantClient client = new TemplateOpsAssistantClient(tool);
        OpsAssistantAnswer answer = client.ask("show me risky payments today");

        assertThat(answer.answer()).contains("payment id");
        assertThat(answer.toolsUsed()).isEmpty();
    }
}
