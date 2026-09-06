package com.ledgerflow.ai;

import org.springframework.stereotype.Service;

@Service
public class OpsAssistantService {

    private final OpsAssistantClient client;

    public OpsAssistantService(OpsAssistantClient client) {
        this.client = client;
    }

    public OpsAssistantAnswer ask(String question) {
        return client.ask(question);
    }
}
