package com.ledgerflow.ai;

import com.ledgerflow.domain.Payment;
import com.ledgerflow.fraud.RiskDecision;

public interface RiskExplanationClient {
    RiskExplanation explain(Payment payment, RiskDecision decision);
}
