package com.ledgerflow.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic fallback for the ops assistant: no LLM call, no network. If the
 * question contains a payment id, look it up directly and report what's known;
 * otherwise say plainly that free-form questions need the real assistant. This is
 * the default provider (safe for local dev, tests, and CI) and never returns
 * silently empty — every path returns a legible answer.
 */
@Component
@ConditionalOnProperty(name = "ledgerflow.ai.provider", havingValue = "template", matchIfMissing = true)
public class TemplateOpsAssistantClient implements OpsAssistantClient {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final OpsAssistantTool tool;

    public TemplateOpsAssistantClient(OpsAssistantTool tool) {
        this.tool = tool;
    }

    @Override
    public OpsAssistantAnswer ask(String question) {
        Matcher m = UUID_PATTERN.matcher(question == null ? "" : question);
        if (m.find()) {
            String id = m.group();
            OpsAssistantTool.PaymentDetail detail = tool.getPaymentDetail(id);
            if (detail == null) {
                return new OpsAssistantAnswer("No payment found with id " + id + ".",
                        List.of("getPaymentDetail"), true);
            }
            String answer = String.format(
                    "Payment %s: status=%s, amount=%.2f %s.%s%s",
                    detail.payment().id(), detail.payment().status(),
                    detail.payment().amountMinor() / 100.0, detail.payment().currency(),
                    detail.riskDecision() != null
                            ? String.format(" Risk decision=%s (p=%.2f).", detail.riskDecision(), detail.fraudProbability())
                            : "",
                    detail.riskExplanation() != null ? " " + detail.riskExplanation() : "");
            return new OpsAssistantAnswer(answer, List.of("getPaymentDetail"), true);
        }
        return new OpsAssistantAnswer(
                "The local fallback assistant can only answer questions that include a payment id "
                        + "(e.g. \"why was payment <uuid> declined\"). Enable ledgerflow.ai.provider=bedrock "
                        + "for free-form questions like \"show me risky payments today\".",
                List.of(), true);
    }
}
