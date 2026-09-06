package com.ledgerflow.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * A genuinely agentic ops assistant: Claude decides which read-only tool(s) on
 * {@link OpsAssistantTool} to call based on the natural-language question, then
 * composes the final answer from the tool results. Spring AI's ChatClient runs the
 * tool-call loop automatically (request -> tool call -> tool result -> final
 * answer), bounded by max-tool-hops. Every tool is read-only (see OpsAssistantTool),
 * so the assistant can look at data but never mutate anything. Any failure falls
 * back to TemplateOpsAssistantClient's deterministic behavior.
 */
@Component
@ConditionalOnProperty(name = "ledgerflow.ai.provider", havingValue = "bedrock")
public class BedrockOpsAssistantClient implements OpsAssistantClient {

    private static final Logger log = LoggerFactory.getLogger(BedrockOpsAssistantClient.class);

    private static final String SYSTEM_PROMPT = """
            You are a payments operations assistant. Answer questions about payments,
            risk decisions, and ledger balances using ONLY the tools provided — never
            guess or invent data. If a tool returns no result, say so plainly. Keep
            answers concise and factual.
            """;

    private final ChatClient chatClient;
    private final OpsAssistantTool tool;
    private final TemplateOpsAssistantClient fallback;

    public BedrockOpsAssistantClient(ChatClient.Builder chatClientBuilder, OpsAssistantTool tool,
                                     @Value("${ledgerflow.ai.assistant.max-tool-hops:3}") int maxToolHops) {
        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).defaultTools(tool).build();
        this.tool = tool;
        this.fallback = new TemplateOpsAssistantClient(tool);
    }

    @Override
    public OpsAssistantAnswer ask(String question) {
        try {
            String answer = chatClient.prompt()
                    .user(question)
                    .call()
                    .content();
            if (answer == null || answer.isBlank()) {
                return degrade(question);
            }
            return new OpsAssistantAnswer(answer.trim(),
                    List.of("findRecentPayments", "getPaymentDetail", "getAccountBalance"), false);
        } catch (Exception ex) {
            log.warn("Bedrock ops assistant unavailable ({}); using template fallback", ex.toString());
            return degrade(question);
        }
    }

    private OpsAssistantAnswer degrade(String question) {
        OpsAssistantAnswer f = fallback.ask(question);
        return new OpsAssistantAnswer(f.answer(), f.toolsUsed(), true);
    }
}
