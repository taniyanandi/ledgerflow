package com.ledgerflow.repository;

import com.ledgerflow.domain.Enums.PaymentStatus;
import com.ledgerflow.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByStatusAndCreatedAtAfterOrderByCreatedAtDesc(PaymentStatus status, Instant since);
    List<Payment> findByCreatedAtAfterOrderByCreatedAtDesc(Instant since);
}
