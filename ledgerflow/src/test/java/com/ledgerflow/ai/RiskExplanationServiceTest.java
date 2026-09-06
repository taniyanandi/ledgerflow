package com.ledgerflow.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerflow.domain.Payment;
import com.ledgerflow.domain.RiskDecisionRecord;
import com.ledgerflow.domain.RiskExplanationRecord;
import com.ledgerflow.exception.ApiException;
import com.ledgerflow.repository.PaymentRepository;
import com.ledgerflow.repository.RiskDecisionRepository;
import com.ledgerflow.repository.RiskExplanationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskExplanationServiceTest {

    @Mock PaymentRepository payments;
    @Mock RiskDecisionRepository decisions;
    @Mock RiskExplanationRepository explanations;
    @Mock RiskExplanationClient client;

    private RiskExplanationService service(ObjectMapper mapper) {
        return new RiskExplanationService(payments, decisions, explanations, client, mapper);
    }

    @Test
    void returnsPersistedExplanationWithoutCallingTheClient() {
        UUID paymentId = UUID.randomUUID();
        RiskExplanationRecord existing = new RiskExplanationRecord(
                UUID.randomUUID(), paymentId, "already explained", "no action", null, true, Instant.now());
        when(explanations.findTopByPaymentIdOrderByCreatedAtDesc(paymentId)).thenReturn(Optional.of(existing));

        RiskExplanationRecord result = service(new ObjectMapper()).findOrGenerate(paymentId);

        assertThat(result).isSameAs(existing);
        verifyNoInteractions(client);
    }

    @Test
    void generatesAndPersistsWhenNoneExists() throws Exception {
        UUID paymentId = UUID.randomUUID();
        ObjectMapper mapper = new ObjectMapper();
        Payment payment = new Payment(paymentId, "ord-1", 5000, "INR");
        String reasonsJson = mapper.writeValueAsString(List.of(
                new com.ledgerflow.fraud.RiskDecision.Reason("device_new", "new device", 0.4)));
        RiskDecisionRecord decisionRecord = new RiskDecisionRecord(
                UUID.randomUUID(), paymentId, 0.8, "DECLINE", reasonsJson, false, Instant.now());

        when(explanations.findTopByPaymentIdOrderByCreatedAtDesc(paymentId)).thenReturn(Optional.empty());
        when(payments.findById(paymentId)).thenReturn(Optional.of(payment));
        when(decisions.findTopByPaymentIdOrderByCreatedAtDesc(paymentId)).thenReturn(Optional.of(decisionRecord));
        when(client.explain(eq(payment), any())).thenReturn(
                new RiskExplanation("generated narrative", "escalate", false, "test-model"));
        when(explanations.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RiskExplanationRecord result = service(mapper).findOrGenerate(paymentId);

        assertThat(result.getNarrative()).isEqualTo("generated narrative");
        assertThat(result.getModelId()).isEqualTo("test-model");
        verify(explanations, times(1)).save(any());
    }

    @Test
    void throws404WhenNoRiskDecisionHasBeenRecorded() {
        UUID paymentId = UUID.randomUUID();
        when(explanations.findTopByPaymentIdOrderByCreatedAtDesc(paymentId)).thenReturn(Optional.empty());
        when(payments.findById(paymentId)).thenReturn(Optional.of(new Payment(paymentId, "ord-1", 5000, "INR")));
        when(decisions.findTopByPaymentIdOrderByCreatedAtDesc(paymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(new ObjectMapper()).findOrGenerate(paymentId))
                .isInstanceOf(ApiException.class);
    }
}
