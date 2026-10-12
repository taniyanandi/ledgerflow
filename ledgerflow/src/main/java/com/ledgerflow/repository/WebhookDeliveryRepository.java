package com.ledgerflow.repository;

import com.ledgerflow.domain.Enums;
import com.ledgerflow.domain.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    /** Finds deliveries that are due for (re)delivery, ordered oldest first. */
    @Query("""
           select d from WebhookDelivery d
           where d.status in :statuses
             and d.nextRetryAt <= :now
           order by d.nextRetryAt asc
           """)
    List<WebhookDelivery> findDeliveriesDue(
            @Param("statuses") List<Enums.DeliveryStatus> statuses,
            @Param("now") Instant now);

    List<WebhookDelivery> findBySubscriptionId(UUID subscriptionId);

    List<WebhookDelivery> findByEventId(UUID eventId);

    long countByStatus(Enums.DeliveryStatus status);

    Page<WebhookDelivery> findBySubscriptionIdOrderByCreatedAtDesc(
            UUID subscriptionId, Pageable page);
}
