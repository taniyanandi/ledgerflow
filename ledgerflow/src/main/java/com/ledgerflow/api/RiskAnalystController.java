package com.ledgerflow.api;

import com.ledgerflow.ai.OpsAssistantService;
import com.ledgerflow.ai.RiskExplanationService;
import com.ledgerflow.api.dto.AskAssistantRequest;
import com.ledgerflow.api.dto.AskAssistantResponse;
import com.ledgerflow.api.dto.RiskExplanationResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * The LLM risk analyst layer: natural-language explanations of already-computed
 * risk decisions, and a tool-calling ops assistant over payment/ledger data. Both
 * run outside the payment saga — see RiskExplanationService/OpsAssistantService.
 */
@RestController
public class RiskAnalystController {

    private final RiskExplanationService explanations;
    private final OpsAssistantService assistant;

    public RiskAnalystController(RiskExplanationService explanations, OpsAssistantService assistant) {
        this.explanations = explanations;
        this.assistant = assistant;
    }

    /** Find-or-generate a natural-language explanation of a payment's risk decision. */
    @GetMapping("/v1/orchestrated-payments/{id}/risk-explanation")
    public RiskExplanationResponse riskExplanation(@PathVariable UUID id) {
        return RiskExplanationResponse.from(explanations.findOrGenerate(id));
    }

    /** Ask the ops assistant a natural-language question about payments/ledger state. */
    @PostMapping("/v1/ops-assistant/ask")
    public AskAssistantResponse ask(@Valid @RequestBody AskAssistantRequest request) {
        return AskAssistantResponse.from(assistant.ask(request.question()));
    }
}
