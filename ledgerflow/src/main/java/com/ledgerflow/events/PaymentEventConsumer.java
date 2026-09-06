package com.ledgerflow.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerflow.domain.PaymentEventRecord;
import com.ledgerflow.repository.PaymentEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes payment events off Kafka and persists them as an append-only audit log.
 * Writes are idempotent on the event id, so redelivery (Kafka is at-least-once)
 * never creates duplicates — the same discipline the payment API itself uses.
 */
@Component
@ConditionalOnProperty(name = "ledgerflow.events.transport", havingValue = "kafka")
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final PaymentEventRepository repository;
    private final ObjectMapper mapper;

    public PaymentEventConsumer(PaymentEventRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @KafkaListener(topics = KafkaPaymentEventPublisher.TOPIC, groupId = "ledgerflow-audit")
    public void onEvent(String payload) {
        try {
            PaymentEvent event = mapper.readValue(payload, PaymentEvent.class);
            if (repository.existsById(event.eventId())) {
                return; // already recorded — at-least-once delivery is fine
            }
            repository.save(new PaymentEventRecord(
                    event.eventId(), event.paymentId(), event.type(),
                    payload, event.occurredAt()));
        } catch (Exception e) {
            log.error("Failed to persist payment event: {}", e.getMessage());
        }
    }
}
