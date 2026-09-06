package com.ledgerflow.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes payment events to Kafka. Active only when
 * {@code ledgerflow.events.transport=kafka} (the default in docker-compose); tests
 * and Kafka-less local runs fall back to {@link LoggingPaymentEventPublisher}.
 *
 * Publishing is best-effort: a broker hiccup is logged, never propagated, so it can
 * never fail an otherwise-successful payment. A production system would harden this
 * with the transactional-outbox pattern (a later phase).
 */
@Component
@ConditionalOnProperty(name = "ledgerflow.events.transport", havingValue = "kafka")
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    public static final String TOPIC = "payment-events";
    private static final Logger log = LoggerFactory.getLogger(KafkaPaymentEventPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    public KafkaPaymentEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
        this.kafka = kafka;
        this.mapper = mapper;
    }

    @Override
    public void publish(PaymentEvent event) {
        try {
            String payload = mapper.writeValueAsString(event);
            kafka.send(TOPIC, event.paymentId().toString(), payload);
        } catch (Exception e) {
            log.warn("Failed to publish {} for payment {}: {}",
                    event.type(), event.paymentId(), e.getMessage());
        }
    }
}
