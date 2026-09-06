package com.ledgerflow.events;

public interface PaymentEventPublisher {
    void publish(PaymentEvent event);
}
