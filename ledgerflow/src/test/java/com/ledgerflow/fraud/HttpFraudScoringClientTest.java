package com.ledgerflow.fraud;

import com.ledgerflow.config.RestClientConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class HttpFraudScoringClientTest {

    @Test
    @Timeout(30)
    void slowFraudServiceFallsBackWithinTheConfiguredReadTimeout() throws Exception {
        // Accepts TCP connections (kernel backlog) but never answers, like a hung service.
        try (ServerSocket silent = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            MockEnvironment env = new MockEnvironment()
                    .withProperty("ledgerflow.fraud.base-url", "http://localhost:" + silent.getLocalPort())
                    .withProperty("ledgerflow.fraud.read-timeout-ms", "500");
            RestClient.Builder builder = RestClient.builder();
            new RestClientConfig().restClientCustomizer().customize(builder);

            HttpFraudScoringClient client = new HttpFraudScoringClient(builder, env);

            long start = System.nanoTime();
            RiskDecision decision = client.score(RiskSignals.defaults(100.0));
            Duration took = Duration.ofNanos(System.nanoTime() - start);

            // The configured 500ms applies, not the 3s default from RestClientConfig.
            assertThat(decision.degraded()).isTrue();
            assertThat(took).isLessThan(Duration.ofSeconds(2));
        }
    }
}
