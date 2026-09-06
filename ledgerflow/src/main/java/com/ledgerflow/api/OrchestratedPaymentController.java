package com.ledgerflow.api;

import com.ledgerflow.api.dto.OrchestratedPaymentRequest;
import com.ledgerflow.api.dto.PaymentResponse;
import com.ledgerflow.domain.Payment;
import com.ledgerflow.domain.PaymentEventRecord;
import com.ledgerflow.orchestration.PaymentOrchestrationService;
import com.ledgerflow.repository.PaymentEventRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/orchestrated-payments")
public class OrchestratedPaymentController {

    private final PaymentOrchestrationService orchestration;
    private final PaymentEventRepository eventRepository;

    public OrchestratedPaymentController(PaymentOrchestrationService orchestration,
                                         PaymentEventRepository eventRepository) {
        this.orchestration = orchestration;
        this.eventRepository = eventRepository;
    }

    /** Runs the full saga: authorize → ML risk check → capture. */
    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @Valid @RequestBody OrchestratedPaymentRequest request) {
        Payment payment = orchestration.process(
                request.merchantReference(), request.amountMinor(),
                request.currency(), request.toSignals());
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentResponse.from(payment));
    }

    /** The persisted event audit trail for a payment (populated by the Kafka consumer). */
    @GetMapping("/{id}/events")
    public List<Map<String, Object>> events(@PathVariable UUID id) {
        return eventRepository.findByPaymentIdOrderByOccurredAtAsc(id).stream()
                .map(this::toView)
                .toList();
    }

    private Map<String, Object> toView(PaymentEventRecord r) {
        return Map.of(
                "eventId", r.getEventId(),
                "type", r.getType(),
                "occurredAt", r.getOccurredAt().toString());
    }
}
