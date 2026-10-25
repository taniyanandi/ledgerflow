package com.ledgerflow.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WebhookDeliveryTest {

    @Test
    void defaultSettingsPreserveExistingBackoffAndDeadLetterAtFifthFailure() {
        WebhookDelivery delivery = newDelivery();
        long[] expectedDelays = {10, 40, 90, 160};

        for (int i = 0; i < expectedDelays.length; i++) {
            Instant before = Instant.now();
            delivery.recordFailure(500, "failure", 5, 10, 2.0);
            assertEquals(Enums.DeliveryStatus.FAILED, delivery.getStatus());
            long actualDelay = Duration.between(before, delivery.getNextRetryAt()).toSeconds();
            assertTrue(actualDelay >= expectedDelays[i] - 1 && actualDelay <= expectedDelays[i] + 1,
                    "Unexpected delay on attempt " + (i + 1) + ": " + actualDelay);
        }

        delivery.recordFailure(500, "failure", 5, 10, 2.0);
        assertEquals(5, delivery.getAttemptCount());
        assertEquals(Enums.DeliveryStatus.DEAD_LETTERED, delivery.getStatus());
        assertNull(delivery.getNextRetryAt());
    }

    @Test
    void customSettingsControlRetryDelayAndMaximumAttempts() {
        WebhookDelivery delivery = newDelivery();

        Instant beforeFirst = Instant.now();
        delivery.recordFailure(500, "failure", 3, 5, 1.0);
        long firstDelay = Duration.between(beforeFirst, delivery.getNextRetryAt()).toSeconds();
        assertTrue(firstDelay >= 4 && firstDelay <= 6);

        Instant beforeSecond = Instant.now();
        delivery.recordFailure(500, "failure", 3, 5, 1.0);
        long secondDelay = Duration.between(beforeSecond, delivery.getNextRetryAt()).toSeconds();
        assertTrue(secondDelay >= 9 && secondDelay <= 11);

        delivery.recordFailure(500, "failure", 3, 5, 1.0);
        assertEquals(3, delivery.getAttemptCount());
        assertEquals(Enums.DeliveryStatus.DEAD_LETTERED, delivery.getStatus());
        assertNull(delivery.getNextRetryAt());
    }

    @Test
    void successMarksDeliveryAsDeliveredAndClearsRetryTime() {
        WebhookDelivery delivery = newDelivery();
        delivery.recordSuccess(200);

        assertEquals(Enums.DeliveryStatus.DELIVERED, delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());
        assertEquals(200, delivery.getLastHttpStatus());
        assertNull(delivery.getNextRetryAt());
        assertNotNull(delivery.getDeliveredAt());
    }

    private WebhookDelivery newDelivery() {
        return new WebhookDelivery(null, UUID.randomUUID(), "payment.captured", "{}");
    }
}
