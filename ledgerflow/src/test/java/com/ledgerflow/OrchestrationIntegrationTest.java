package com.ledgerflow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the saga end-to-end. Runs under the 'test' profile: no Kafka broker,
 * and the fraud service URL is intentionally unreachable, so the risk step uses the
 * rules-based fallback — which lets these tests assert the APPROVE and DECLINE paths
 * deterministically without any external process.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class OrchestrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mvc;

    @Test
    void lowRiskPaymentIsCaptured() throws Exception {
        String body = """
                {"merchantReference":"ord-1","amountMinor":5000,"currency":"INR",
                 "risk":{"amountZscore":0.1,"txnVelocity1h":1,"geoDistanceKm":4,
                         "deviceNew":0,"hourOfDay":14,"isForeign":0,"cardAgeDays":900}}
                """;
        mvc.perform(post("/v1/orchestrated-payments")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("CAPTURED")));
    }

    @Test
    void highRiskPaymentIsDeclined() throws Exception {
        // Six risk flags -> the fallback returns DECLINE -> saga aborts -> 402.
        String body = """
                {"merchantReference":"ord-2","amountMinor":900000,"currency":"INR",
                 "risk":{"amountZscore":4.0,"txnVelocity1h":9,"geoDistanceKm":1200,
                         "deviceNew":1,"hourOfDay":3,"isForeign":1,"cardAgeDays":5}}
                """;
        mvc.perform(post("/v1/orchestrated-payments")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPaymentRequired());
    }
}
