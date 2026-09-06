package com.ledgerflow.fraud;

public interface FraudScoringClient {
    RiskDecision score(RiskSignals signals);
}
