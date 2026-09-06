package com.ledgerflow.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AskAssistantRequest(@NotBlank String question) {}
