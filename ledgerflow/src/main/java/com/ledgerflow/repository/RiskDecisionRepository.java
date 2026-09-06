package com.ledgerflow.repository;

import com.ledgerflow.domain.RiskDecisionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RiskDecisionRepository extends JpaRepository<RiskDecisionRecord, UUID> {
    Optional<RiskDecisionRecord> findTopByPaymentIdOrderByCreatedAtDesc(UUID paymentId);
}
