package com.ledgerflow.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default event transport when Kafka is not enabled (tests, local runs without a
 * broker). Keeps the rest of the system oblivious to whether Kafka is present.
 */
@Component
@ConditionalOnProperty(name = "ledgerflow.events.transport", havingValue = "noop", matchIfMissing = true)
public class LoggingPaymentEventPublisher implements PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingPaymentEventPublisher.class);

    @Override
    public void publish(PaymentEvent event) {
        log.info("[event] {} payment={} status={} detail={}",
                event.type(), event.paymentId(), event.status(), event.detail());
    }
}
