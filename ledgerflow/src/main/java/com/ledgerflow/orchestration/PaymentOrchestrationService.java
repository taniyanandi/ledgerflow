package com.ledgerflow.orchestration;

import com.ledgerflow.domain.Enums.PaymentStatus;
import com.ledgerflow.domain.Payment;
import com.ledgerflow.events.PaymentEvent;
import com.ledgerflow.events.PaymentEventPublisher;
import com.ledgerflow.exception.ApiException;
import com.ledgerflow.fraud.FraudScoringClient;
import com.ledgerflow.fraud.RiskDecision;
import com.ledgerflow.fraud.RiskSignals;
import com.ledgerflow.orchestration.saga.SagaExecutor;
import com.ledgerflow.orchestration.saga.SagaException;
import com.ledgerflow.orchestration.saga.SagaStep;
import com.ledgerflow.repository.PaymentRepository;
import com.ledgerflow.service.LedgerService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Drives a payment through an explicit saga:
 *
 *   Authorize → RiskCheck (ML fraud service) → Capture (double-entry ledger)
 *
 * Each step advances the payment through the state machine and emits an event. If
 * risk declines, or any step fails, completed steps are compensated in reverse and
 * the payment ends FAILED — never half-done. This is the difference between a demo
 * that posts a ledger row and a system that stays consistent when things go wrong.
 */
@Service
public class PaymentOrchestrationService {

    private final PaymentRepository payments;
    private final PaymentStateMachine stateMachine;
    private final LedgerService ledger;
    private final FraudScoringClient fraud;
    private final PaymentEventPublisher events;

    public PaymentOrchestrationService(PaymentRepository payments,
                                       PaymentStateMachine stateMachine,
                                       LedgerService ledger,
                                       FraudScoringClient fraud,
                                       PaymentEventPublisher events) {
        this.payments = payments;
        this.stateMachine = stateMachine;
        this.ledger = ledger;
        this.fraud = fraud;
        this.events = events;
    }

    @Transactional
    public Payment process(String merchantReference, long amountMinor,
                           String currency, RiskSignals signals) {
        Payment payment = new Payment(UUID.randomUUID(), merchantReference, amountMinor, currency);
        payments.save(payment);

        PaymentContext ctx = new PaymentContext(payment, signals);
        SagaExecutor<PaymentContext> saga = new SagaExecutor<>(List.of(
                authorizeStep(), riskCheckStep(), captureStep()));

        try {
            saga.run(ctx);
        } catch (SagaException e) {
            transition(payment, PaymentStatus.FAILED);
            emit("PaymentFailed", payment, "aborted at step " + e.getFailedStep());
            if (e.getCause() instanceof ApiException api) {
                throw api;   // surface a meaningful HTTP status (e.g. 402 for declined)
            }
            throw e;
        }
        return payment;
    }

    // --- steps --------------------------------------------------------------

    private SagaStep<PaymentContext> authorizeStep() {
        return new SagaStep<>() {
            public String name() { return "authorize"; }

            public void execute(PaymentContext ctx) {
                transition(ctx.getPayment(), PaymentStatus.AUTHORIZED);
                emit("PaymentAuthorized", ctx.getPayment(), "authorization hold placed");
            }

            public void compensate(PaymentContext ctx) {
                // Release the authorization hold. Terminal FAILED is set by the caller.
                emit("PaymentAuthorizationReleased", ctx.getPayment(), "hold released");
            }
        };
    }

    private SagaStep<PaymentContext> riskCheckStep() {
        return new SagaStep<>() {
            public String name() { return "risk-check"; }

            public void execute(PaymentContext ctx) {
                RiskDecision decision = fraud.score(ctx.getSignals());
                ctx.setRiskDecision(decision);
                if (decision.isBlocked()) {
                    emit("PaymentDeclined", ctx.getPayment(),
                            "fraud p=" + decision.fraudProbability()
                                    + (decision.degraded() ? " (degraded)" : ""));
                    throw new ApiException(HttpStatus.PAYMENT_REQUIRED,
                            "Payment declined by risk engine (p="
                                    + decision.fraudProbability() + ")");
                }
                emit("RiskApproved", ctx.getPayment(),
                        "decision=" + decision.decision() + " p=" + decision.fraudProbability());
            }
        };
    }

    private SagaStep<PaymentContext> captureStep() {
        return new SagaStep<>() {
            public String name() { return "capture"; }

            public void execute(PaymentContext ctx) {
                ledger.postCapture(ctx.getPayment());
                transition(ctx.getPayment(), PaymentStatus.CAPTURED);
                emit("PaymentCaptured", ctx.getPayment(), "funds captured, ledger posted");
            }

            public void compensate(PaymentContext ctx) {
                // Reverse the ledger posting if a later step ever fails.
                ledger.postRefund(ctx.getPayment());
                emit("PaymentCaptureReversed", ctx.getPayment(), "ledger capture reversed");
            }
        };
    }

    // --- helpers ------------------------------------------------------------

    private void transition(Payment payment, PaymentStatus to) {
        stateMachine.assertCanTransition(payment.getStatus(), to);
        payment.transitionTo(to);
        payments.save(payment);
    }

    private void emit(String type, Payment p, String detail) {
        events.publish(PaymentEvent.of(type, p.getId(), p.getAmountMinor(),
                p.getCurrency(), p.getStatus().name(), detail));
    }
}
