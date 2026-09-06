package com.ledgerflow.repository;

import com.ledgerflow.domain.RiskExplanationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RiskExplanationRepository extends JpaRepository<RiskExplanationRecord, UUID> {
    Optional<RiskExplanationRecord> findTopByPaymentIdOrderByCreatedAtDesc(UUID paymentId);
}
