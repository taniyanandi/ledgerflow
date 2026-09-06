package com.ledgerflow.repository;

import com.ledgerflow.domain.PaymentEventRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentEventRepository extends JpaRepository<PaymentEventRecord, UUID> {
    List<PaymentEventRecord> findByPaymentIdOrderByOccurredAtAsc(UUID paymentId);
}
