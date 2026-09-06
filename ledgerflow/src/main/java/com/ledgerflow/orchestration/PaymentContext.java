package com.ledgerflow.orchestration;

import com.ledgerflow.domain.Payment;
import com.ledgerflow.fraud.RiskDecision;
import com.ledgerflow.fraud.RiskSignals;

/** Mutable state passed through every step of the payment saga. */
public class PaymentContext {
    private final Payment payment;
    private final RiskSignals signals;
    private RiskDecision riskDecision;

    public PaymentContext(Payment payment, RiskSignals signals) {
        this.payment = payment;
        this.signals = signals;
    }

    public Payment getPayment() { return payment; }
    public RiskSignals getSignals() { return signals; }
    public RiskDecision getRiskDecision() { return riskDecision; }
    public void setRiskDecision(RiskDecision riskDecision) { this.riskDecision = riskDecision; }
}
