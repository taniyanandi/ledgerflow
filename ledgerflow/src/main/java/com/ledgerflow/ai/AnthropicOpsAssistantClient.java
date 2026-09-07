package com.ledgerflow.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;

/**
 * An LLM-backed ops assistant: real data is fetched deterministically (the payment
 * id is extracted from the question and looked up via {@link OpsAssistantTool} —
 * exactly like {@link TemplateOpsAssistantClient}, so there is never any guessing
 * about which payment is meant), then Claude composes the final answer strictly
 * from those retrieved facts. This is intentionally not open-ended tool-calling:
 * grounding the LLM in a fixed, already-fetched fact set makes hallucination
 * structurally impossible rather than merely instructed against. Any failure falls
 * back to TemplateOpsAssistantClient's deterministic formatting.
 */
@Component
@ConditionalOnProperty(name = "ledgerflow.ai.provider", havingValue = "anthropic")
public class AnthropicOpsAssistantClient implements OpsAssistantClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicOpsAssistantClient.class);
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private static final String SYSTEM_PROMPT = """
            You are a payments operations assistant. You will be given the question an
            operator asked, plus the real, already-retrieved facts about one payment.
            Answer using ONLY those facts — never guess or invent data. Keep the answer
            concise (2-3 sentences) and factual.
            """;

    private final RestClient client;
    private final OpsAssistantTool tool;
    private final TemplateOpsAssistantClient fallback;
    private final String model;
    private final int maxTokens;

    public AnthropicOpsAssistantClient(RestClient.Builder builder, OpsAssistantTool tool,
                                       @Value("${ledgerflow.ai.anthropic.api-key}") String apiKey,
                                       @Value("${ledgerflow.ai.anthropic.model}") String model,
                                       @Value("${ledgerflow.ai.anthropic.timeout-ms}") int timeoutMs,
                                       @Value("${ledgerflow.ai.anthropic.max-tokens}") int maxTokens) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        factory.setReadTimeout(timeoutMs);
        this.client = builder.baseUrl(API_URL)
                .requestFactory(factory)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                .build();
        this.tool = tool;
        this.fallback = new TemplateOpsAssistantClient(tool);
        this.model = model;
        this.maxTokens = maxTokens;
    }

    @Override
    public OpsAssistantAnswer ask(String question) {
        Matcher m = TemplateOpsAssistantClient.UUID_PATTERN.matcher(question == null ? "" : question);
        if (!m.find()) {
            return degrade(question);
        }
        String id = m.group();
        OpsAssistantTool.PaymentDetail detail = tool.getPaymentDetail(id);
        if (detail == null) {
            return new OpsAssistantAnswer("No payment found with id " + id + ".",
                    List.of("getPaymentDetail"), false);
        }

        try {
            String prompt = buildPrompt(question, detail);
            AnthropicRiskExplanationClient.MessagesResponse response = client.post()
                    .body(new AnthropicRiskExplanationClient.MessagesRequest(model, maxTokens, SYSTEM_PROMPT,
                            List.of(new AnthropicRiskExplanationClient.Message("user", prompt))))
                    .retrieve()
                    .body(AnthropicRiskExplanationClient.MessagesResponse.class);

            String answer = AnthropicRiskExplanationClient.extractText(response);
            if (answer == null || answer.isBlank()) {
                return degrade(question);
            }
            return new OpsAssistantAnswer(answer.trim(), List.of("getPaymentDetail"), false);
        } catch (Exception ex) {
            log.warn("Anthropic ops assistant unavailable ({}); using template fallback", ex.toString());
            return degrade(question);
        }
    }

    static String buildPrompt(String question, OpsAssistantTool.PaymentDetail detail) {
        return String.format("""
                Operator question: %s

                Payment facts:
                - id: %s
                - status: %s
                - amount: %.2f %s
                - risk decision: %s (fraud probability %s)
                - risk explanation: %s
                """,
                question, detail.payment().id(), detail.payment().status(),
                detail.payment().amountMinor() / 100.0, detail.payment().currency(),
                detail.riskDecision() == null ? "none recorded" : detail.riskDecision(),
                detail.fraudProbability() == null ? "n/a" : detail.fraudProbability(),
                detail.riskExplanation() == null ? "none generated yet" : detail.riskExplanation());
    }

    private OpsAssistantAnswer degrade(String question) {
        OpsAssistantAnswer f = fallback.ask(question);
        return new OpsAssistantAnswer(f.answer(), f.toolsUsed(), true);
    }
}
