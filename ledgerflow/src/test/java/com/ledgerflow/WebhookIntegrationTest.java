package com.ledgerflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerflow.api.dto.CreateWebhookRequest;
import com.ledgerflow.webhook.WebhookSigningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.ledgerflow.domain.WebhookDelivery;
import com.ledgerflow.domain.WebhookSubscription;
import com.ledgerflow.repository.WebhookDeliveryRepository;
import com.ledgerflow.repository.WebhookSubscriptionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the webhook subscription API and HMAC signing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class WebhookIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("ledgerflow.events.transport", () -> "noop");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired WebhookSigningService signing;
    @Autowired
    WebhookDeliveryRepository deliveries;

    @Autowired
    WebhookSubscriptionRepository subscriptions;
    @Autowired
    JdbcTemplate jdbcTemplate;

    private UUID insertPaymentEvent(String eventType) {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        jdbcTemplate.update("""
            INSERT INTO payment_events
                (event_id, payment_id, type, payload, occurred_at)
            VALUES (?, ?, ?, ?, ?)
            """,
                eventId,
                paymentId,
                eventType,
                "{}",
                Timestamp.from(Instant.now()));

        return eventId;
    }

    @Test
    void createAndListWebhookSubscriptions() throws Exception {
        CreateWebhookRequest request = new CreateWebhookRequest(
                "Test Endpoint", "https://example.com/webhook", "my-secret-key");

        // Create a subscription.
        mvc.perform(post("/v1/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Test Endpoint")))
                .andExpect(jsonPath("$.url", is("https://example.com/webhook")))
                .andExpect(jsonPath("$.active", is(true)));

        // List subscriptions — should see the one we just created.
        mvc.perform(get("/v1/webhooks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].name", is("Test Endpoint")));
    }

    @Test
    void deactivateAndReactivateSubscription() throws Exception {
        CreateWebhookRequest request = new CreateWebhookRequest(
                "Deactivation Test", "https://example.com/deactivate", "secret");

        String response = mvc.perform(post("/v1/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = mapper.readTree(response).get("id").asText();

        // Deactivate.
        mvc.perform(delete("/v1/webhooks/" + id))
                .andExpect(status().isNoContent());

        mvc.perform(get("/v1/webhooks/" + id))
                .andExpect(jsonPath("$.active", is(false)));

        // Reactivate.
        mvc.perform(post("/v1/webhooks/" + id + "/activate"))
                .andExpect(jsonPath("$.active", is(true)));
    }

    @Test
    void hmacSignatureIsConsistentAndVerifiable() {
        String payload = "{\"type\":\"PaymentCaptured\",\"amount\":150000}";
        String secret = "test-secret-key";

        String sig1 = signing.sign(payload, secret);
        String sig2 = signing.sign(payload, secret);

        // Same input must produce the same signature.
        assert sig1.equals(sig2) : "HMAC signatures should be deterministic";
        assert sig1.startsWith("sha256=") : "Signature should be prefixed with sha256=";

        // Different payload must produce a different signature.
        String sig3 = signing.sign(payload + "x", secret);
        assert !sig1.equals(sig3) : "Different payloads should produce different signatures";

        // Different secret must produce a different signature.
        String sig4 = signing.sign(payload, secret + "x");
        assert !sig1.equals(sig4) : "Different secrets should produce different signatures";
    }



    @Test
    void listWebhookDeliveriesReturnsNewestFirstWithPagination() throws Exception {
        CreateWebhookRequest request = new CreateWebhookRequest(
                "Delivery History Test",
                "https://example.com/deliveries",
                "test-secret");

        String response = mvc.perform(post("/v1/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID subscriptionId = UUID.fromString(
                mapper.readTree(response).get("id").asText());

        WebhookSubscription subscription = subscriptions.findById(subscriptionId)
                .orElseThrow();

        // Insert the corresponding payment events before creating deliveries.
        UUID oldestEventId = insertPaymentEvent("FailedEvent");
        WebhookDelivery oldest = new WebhookDelivery(
                subscription, oldestEventId, "FailedEvent", "{}");
        oldest.recordFailure(500, "Server error");
        deliveries.saveAndFlush(oldest);

        Thread.sleep(20);

        UUID middleEventId = insertPaymentEvent("DeliveredEvent");
        WebhookDelivery middle = new WebhookDelivery(
                subscription, middleEventId, "DeliveredEvent", "{}");
        middle.recordSuccess(200);
        deliveries.saveAndFlush(middle);

        Thread.sleep(20);

        UUID newestEventId = insertPaymentEvent("PendingEvent");
        WebhookDelivery newest = new WebhookDelivery(
                subscription, newestEventId, "PendingEvent", "{}");
        deliveries.saveAndFlush(newest);

        // First page: newest two deliveries.
        mvc.perform(get("/v1/webhooks/" + subscriptionId + "/deliveries")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.totalPages", is(2)))
                .andExpect(jsonPath("$.content[0].eventType", is("PendingEvent")))
                .andExpect(jsonPath("$.content[0].status", is("PENDING")))
                .andExpect(jsonPath("$.content[0].lastAttemptAt").doesNotExist())
                .andExpect(jsonPath("$.content[1].eventType", is("DeliveredEvent")))
                .andExpect(jsonPath("$.content[1].status", is("DELIVERED")))
                .andExpect(jsonPath("$.content[1].httpStatusCode", is(200)))
                .andExpect(jsonPath("$.content[1].lastAttemptAt").isNotEmpty());

        // Second page: the oldest delivery.
        mvc.perform(get("/v1/webhooks/" + subscriptionId + "/deliveries")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].eventType", is("FailedEvent")))
                .andExpect(jsonPath("$.content[0].status", is("FAILED")))
                .andExpect(jsonPath("$.content[0].httpStatusCode", is(500)))
                .andExpect(jsonPath("$.content[0].lastAttemptAt").isNotEmpty());
    }


}
