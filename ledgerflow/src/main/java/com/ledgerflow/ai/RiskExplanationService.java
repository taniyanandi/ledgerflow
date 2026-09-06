package com.ledgerflow.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerflow.domain.Payment;
import com.ledgerflow.domain.RiskDecisionRecord;
import com.ledgerflow.domain.RiskExplanationRecord;
import com.ledgerflow.exception.ApiException;
import com.ledgerflow.fraud.RiskDecision;
import com.ledgerflow.repository.PaymentRepository;
import com.ledgerflow.repository.RiskDecisionRepository;
import com.ledgerflow.repository.RiskExplanationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Find-or-generate for risk explanations. This is the single choke point: it never
 * regenerates a narrative for a payment that already has one, and it runs entirely
 * outside the payment saga — the risk decision it explains was already computed and
 * persisted by {@code PaymentOrchestrationService} before this is ever called.
 */
@Service
public class RiskExplanationService {

    private final PaymentRepository payments;
    private final RiskDecisionRepository decisions;
    private final RiskExplanationRepository explanations;
    private final RiskExplanationClient client;
    private final ObjectMapper objectMapper;

    public RiskExplanationService(PaymentRepository payments,
                                  RiskDecisionRepository decisions,
                                  RiskExplanationRepository explanations,
                                  RiskExplanationClient client,
                                  ObjectMapper objectMapper) {
        this.payments = payments;
        this.decisions = decisions;
        this.explanations = explanations;
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RiskExplanationRecord findOrGenerate(UUID paymentId) {
        return explanations.findTopByPaymentIdOrderByCreatedAtDesc(paymentId)
                .orElseGet(() -> generate(paymentId));
    }

    private RiskExplanationRecord generate(UUID paymentId) {
        Payment payment = payments.findById(paymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found: " + paymentId));
        RiskDecisionRecord decisionRecord = decisions.findTopByPaymentIdOrderByCreatedAtDesc(paymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "No risk decision recorded for payment: " + paymentId));

        RiskDecision decision = toRiskDecision(decisionRecord);
        RiskExplanation explanation = client.explain(payment, decision);

        RiskExplanationRecord record = new RiskExplanationRecord(
                UUID.randomUUID(), paymentId, explanation.narrative(),
                explanation.recommendedAction(), explanation.modelId(),
                explanation.degraded(), Instant.now());
        return explanations.save(record);
    }

    private RiskDecision toRiskDecision(RiskDecisionRecord r) {
        try {
            List<RiskDecision.Reason> reasons = objectMapper.readValue(
                    r.getReasons(), new com.fasterxml.jackson.core.type.TypeReference<List<RiskDecision.Reason>>() {});
            return new RiskDecision(r.getFraudProbability(),
                    RiskDecision.Decision.valueOf(r.getDecision()), reasons, r.isDegraded());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Corrupt risk decision record for payment: " + r.getPaymentId());
        }
    }
}
