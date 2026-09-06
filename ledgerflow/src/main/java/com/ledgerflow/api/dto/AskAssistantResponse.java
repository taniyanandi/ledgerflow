package com.ledgerflow.api.dto;

import com.ledgerflow.ai.OpsAssistantAnswer;

import java.util.List;

public record AskAssistantResponse(String answer, List<String> toolsUsed, boolean degraded) {
    public static AskAssistantResponse from(OpsAssistantAnswer a) {
        return new AskAssistantResponse(a.answer(), a.toolsUsed(), a.degraded());
    }
}
