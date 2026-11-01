package com.ledgerflow.webhook;

import com.ledgerflow.config.WebhookProperties;
import com.ledgerflow.domain.Enums;
import com.ledgerflow.domain.WebhookDelivery;
import com.ledgerflow.domain.WebhookSubscription;
import com.ledgerflow.repository.WebhookDeliveryRepository;
import com.ledgerflow.repository.WebhookSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Manages webhook delivery lifecycle: creates delivery records for each active
 * subscriber when an event occurs, sends the signed payload, and retries failed
 * deliveries with exponential backoff. Dead-lettered deliveries (exceeded max
 * attempts) are retained for manual investigation.
 */
@Service
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);
    private static final String SIGNATURE_HEADER = "X-LedgerFlow-Signature";
    private static final String EVENT_ID_HEADER = "X-LedgerFlow-Event-Id";

    private final WebhookSubscriptionRepository subscriptions;
    private final WebhookDeliveryRepository deliveries;
    private final WebhookSigningService signing;
    private final RestTemplate restTemplate;
    private final WebhookProperties webhookProperties;

    public WebhookDeliveryService(WebhookSubscriptionRepository subscriptions,
                                  WebhookDeliveryRepository deliveries,
                                  WebhookSigningService signing,
                                  WebhookProperties webhookProperties) {
        this.subscriptions = subscriptions;
        this.deliveries = deliveries;
        this.signing = signing;
        this.webhookProperties = webhookProperties;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Creates delivery records for every active subscriber. Called when a payment
     * event is persisted — one delivery per subscriber, each starting in PENDING.
     */
    @Transactional
    public void enqueueForAllSubscribers(UUID eventId, String eventType, String payload) {
        List<WebhookSubscription> active = subscriptions.findByActiveTrue();
        if (active.isEmpty()) {
            log.debug("No active webhook subscribers — skipping delivery for event {}", eventId);
            return;
        }

        for (WebhookSubscription sub : active) {
            WebhookDelivery delivery = new WebhookDelivery(sub, eventId, eventType, payload);
            deliveries.save(delivery);
            log.debug("Enqueued webhook delivery {} for subscriber '{}' (event {})",
                    delivery.getId(), sub.getName(), eventId);
        }
    }

    /**
     * Polls for pending/failed deliveries that are due and attempts to deliver them.
     * Runs every 10 seconds. Each delivery is attempted independently — one failure
     * does not block others.
     */
    @Scheduled(fixedDelay = 10_000)
    @Transactional
    public void processDeliveries() {
        List<WebhookDelivery> due = deliveries.findDeliveriesDue(
                List.of(Enums.DeliveryStatus.PENDING, Enums.DeliveryStatus.FAILED),
                Instant.now());

        for (WebhookDelivery delivery : due) {
            attemptDelivery(delivery);
        }

        if (!due.isEmpty()) {
            log.info("Processed {} webhook deliveries", due.size());
        }
    }

    private void attemptDelivery(WebhookDelivery delivery) {
        WebhookSubscription sub = delivery.getSubscription();
        String payload = delivery.getPayload();

        try {
            String signature = signing.sign(payload, sub.getSecret());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(SIGNATURE_HEADER, signature);
            headers.set(EVENT_ID_HEADER, delivery.getEventId().toString());

            HttpEntity<String> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(sub.getUrl(), request, String.class);

            int statusCode = response.getStatusCode().value();
            if (response.getStatusCode().is2xxSuccessful()) {
                delivery.recordSuccess(statusCode);
                log.debug("Webhook delivered: {} → {} (HTTP {})",
                        delivery.getId(), sub.getUrl(), statusCode);
            } else {
                recordFailure(delivery, statusCode, "Non-2xx response: " + statusCode);
                log.warn("Webhook delivery {} returned HTTP {} (attempt {})",
                        delivery.getId(), statusCode, delivery.getAttemptCount());
            }
        } catch (Exception e) {
            delivery.recordNetworkFailure(e.getMessage(), webhookProperties.getMaxAttempts(),
                    webhookProperties.getInitialBackoffSeconds(), webhookProperties.getBackoffMultiplier());
            log.warn("Webhook delivery {} failed: {} (attempt {})",
                    delivery.getId(), e.getMessage(), delivery.getAttemptCount());
        }

        if (delivery.getStatus() == Enums.DeliveryStatus.DEAD_LETTERED) {
            log.error("Webhook delivery {} dead-lettered after {} attempts for subscriber '{}'",
                    delivery.getId(), delivery.getAttemptCount(), sub.getName());
        }

        deliveries.save(delivery);
    }

    private void recordFailure(WebhookDelivery delivery, int statusCode, String error) {
        delivery.recordFailure(statusCode, error, webhookProperties.getMaxAttempts(),
                webhookProperties.getInitialBackoffSeconds(), webhookProperties.getBackoffMultiplier());
    }
}
