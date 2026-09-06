package com.ledgerflow.fraud;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Calls the Python fraud-service over HTTP. If that service is slow or down, we do
 * NOT fail the payment: a conservative rules-based fallback produces a decision and
 * flags it as degraded, so the platform keeps running (fail-soft) while operators
 * are alerted by the {@code degraded} flag flowing through to events.
 */
@Component
public class HttpFraudScoringClient implements FraudScoringClient {

    private static final Logger log = LoggerFactory.getLogger(HttpFraudScoringClient.class);

    private final RestClient client;

    public HttpFraudScoringClient(RestClient.Builder builder,
                                  org.springframework.core.env.Environment env) {
        String baseUrl = env.getProperty("ledgerflow.fraud.base-url", "http://localhost:8000");
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public RiskDecision score(RiskSignals s) {
        try {
            ScoreApiResponse r = client.post()
                    .uri("/score")
                    .body(toApiRequest(s))
                    .retrieve()
                    .body(ScoreApiResponse.class);
            if (r == null) {
                return fallback(s, "empty response");
            }
            List<RiskDecision.Reason> reasons = r.topReasons() == null ? List.of()
                    : r.topReasons().stream()
                        .map(x -> new RiskDecision.Reason(x.feature(), x.explanation(), x.contribution()))
                        .toList();
            return new RiskDecision(
                    r.fraudProbability(),
                    RiskDecision.Decision.valueOf(r.decision()),
                    reasons,
                    false);
        } catch (Exception ex) {
            log.warn("Fraud service unavailable ({}); using rules-based fallback", ex.toString());
            return fallback(s, ex.getMessage());
        }
    }

    /** Simple, explainable heuristic used only when the model service is unreachable. */
    private RiskDecision fallback(RiskSignals s, String cause) {
        int flags = 0;
        if (s.amountZscore() > 3.0) flags++;
        if (s.txnVelocity1h() >= 6) flags++;
        if (s.geoDistanceKm() > 500) flags++;
        if (s.deviceNew() == 1) flags++;
        if (s.isForeign() == 1) flags++;
        if (s.cardAgeDays() < 30) flags++;

        RiskDecision.Decision decision = flags >= 4 ? RiskDecision.Decision.DECLINE
                : flags >= 2 ? RiskDecision.Decision.REVIEW
                : RiskDecision.Decision.APPROVE;

        return new RiskDecision(
                Math.min(1.0, flags / 6.0),
                decision,
                List.of(new RiskDecision.Reason("fallback",
                        "rules-based fallback (" + flags + " risk flags)", flags)),
                true);
    }

    private ScoreApiRequest toApiRequest(RiskSignals s) {
        return new ScoreApiRequest(s.amount(), s.amountZscore(), s.txnVelocity1h(),
                s.geoDistanceKm(), s.deviceNew(), s.hourOfDay(), s.isForeign(), s.cardAgeDays());
    }

    // --- wire DTOs matching the Python API's snake_case schema ---

    record ScoreApiRequest(
            double amount,
            @JsonProperty("amount_zscore") double amountZscore,
            @JsonProperty("txn_velocity_1h") int txnVelocity1h,
            @JsonProperty("geo_distance_km") double geoDistanceKm,
            @JsonProperty("device_new") int deviceNew,
            @JsonProperty("hour_of_day") int hourOfDay,
            @JsonProperty("is_foreign") int isForeign,
            @JsonProperty("card_age_days") int cardAgeDays) {}

    record ApiReason(String feature, String explanation, double contribution) {}

    record ScoreApiResponse(
            @JsonProperty("fraud_probability") double fraudProbability,
            String decision,
            double threshold,
            @JsonProperty("top_reasons") List<ApiReason> topReasons,
            @JsonProperty("model_version") String modelVersion) {}
}
