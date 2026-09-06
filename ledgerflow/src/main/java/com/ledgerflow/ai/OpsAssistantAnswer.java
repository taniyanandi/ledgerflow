package com.ledgerflow.ai;

import java.util.List;

public record OpsAssistantAnswer(String answer, List<String> toolsUsed, boolean degraded) {}
