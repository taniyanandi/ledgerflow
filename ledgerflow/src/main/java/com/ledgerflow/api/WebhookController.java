package com.ledgerflow.api;

import com.ledgerflow.api.dto.CreateWebhookRequest;
import com.ledgerflow.api.dto.WebhookSubscriptionResponse;
import com.ledgerflow.domain.WebhookSubscription;
import com.ledgerflow.exception.ApiException;
import com.ledgerflow.repository.WebhookSubscriptionRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.ledgerflow.api.dto.WebhookDeliveryResponse;
import com.ledgerflow.domain.WebhookDelivery;
import com.ledgerflow.repository.WebhookDeliveryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/webhooks")
public class WebhookController {

    private final WebhookSubscriptionRepository subscriptions;
    private final WebhookDeliveryRepository deliveries;

    public WebhookController(
            WebhookSubscriptionRepository subscriptions,
            WebhookDeliveryRepository deliveries) {
        this.subscriptions = subscriptions;
        this.deliveries = deliveries;
    }

    @PostMapping
    public ResponseEntity<WebhookSubscriptionResponse> create(@Valid @RequestBody CreateWebhookRequest request) {
        WebhookSubscription sub = new WebhookSubscription(request.name(), request.url(), request.secret());
        subscriptions.save(sub);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(WebhookSubscriptionResponse.from(sub));
    }

    @GetMapping
    public List<WebhookSubscriptionResponse> list() {
        return subscriptions.findAll().stream()
                .map(WebhookSubscriptionResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public WebhookSubscriptionResponse get(@PathVariable UUID id) {
        return WebhookSubscriptionResponse.from(findOrThrow(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        WebhookSubscription sub = findOrThrow(id);
        sub.deactivate();
        subscriptions.save(sub);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    public WebhookSubscriptionResponse activate(@PathVariable UUID id) {
        WebhookSubscription sub = findOrThrow(id);
        sub.activate();
        subscriptions.save(sub);
        return WebhookSubscriptionResponse.from(sub);
    }

    @GetMapping("/{subscriptionId}/deliveries")
    public Page<WebhookDeliveryResponse> listDeliveries(
            @PathVariable UUID subscriptionId,
            Pageable pageable) {

        findOrThrow(subscriptionId);

        return deliveries
                .findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId, pageable)
                .map(WebhookDeliveryResponse::from);
    }

    private WebhookSubscription findOrThrow(UUID id) {
        return subscriptions.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Webhook subscription not found: " + id));
    }
}
