package com.ledgerflow.ai;

import com.ledgerflow.domain.Payment;
import com.ledgerflow.domain.RiskDecisionRecord;
import com.ledgerflow.domain.RiskExplanationRecord;
import com.ledgerflow.repository.PaymentEventRepository;
import com.ledgerflow.repository.PaymentRepository;
import com.ledgerflow.repository.RiskDecisionRepository;
import com.ledgerflow.repository.RiskExplanationRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Read-only data access for the ops assistant. Every method here is a plain query
 * against existing repositories — nothing here can mutate a payment or any other
 * state. This is a hard boundary: the ops assistant may look at data and reason
 * about it, but it can never take an action.
 */
@Component
public class OpsAssistantTool {

    private final PaymentRepository payments;
    private final PaymentEventRepository events;
    private final RiskDecisionRepository decisions;
    private final RiskExplanationRepository explanations;

    public OpsAssistantTool(PaymentRepository payments, PaymentEventRepository events,
                            RiskDecisionRepository decisions,
                            RiskExplanationRepository explanations) {
        this.payments = payments;
        this.events = events;
        this.decisions = decisions;
        this.explanations = explanations;
    }

    /** Full detail for one payment: status, amount, latest risk decision, and any generated explanation. */
    public PaymentDetail getPaymentDetail(String paymentId) {
        UUID id = UUID.fromString(paymentId);
        Payment payment = payments.findById(id).orElse(null);
        if (payment == null) {
            return null;
        }
        RiskDecisionRecord decision = decisions.findTopByPaymentIdOrderByCreatedAtDesc(id).orElse(null);
        RiskExplanationRecord explanation = explanations.findTopByPaymentIdOrderByCreatedAtDesc(id).orElse(null);
        long eventCount = events.findByPaymentIdOrderByOccurredAtAsc(id).size();
        return new PaymentDetail(
                PaymentSummary.from(payment),
                decision == null ? null : decision.getFraudProbability(),
                decision == null ? null : decision.getDecision(),
                decision == null ? null : decision.getReasons(),
                explanation == null ? null : explanation.getNarrative(),
                (int) eventCount);
    }

    public record PaymentSummary(UUID id, String merchantReference, long amountMinor,
                                 String currency, String status, String createdAt) {
        static PaymentSummary from(Payment p) {
            return new PaymentSummary(p.getId(), p.getMerchantReference(), p.getAmountMinor(),
                    p.getCurrency(), p.getStatus().name(), p.getCreatedAt().toString());
        }
    }

    public record PaymentDetail(PaymentSummary payment, Double fraudProbability,
                                String riskDecision, String reasonsJson,
                                String riskExplanation, int eventCount) {}
}
