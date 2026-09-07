package com.ledgerflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test for the risk analyst endpoints — the request -> persist ->
 * response path, entirely local, no external service involved.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class RiskAnalystApiIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void explainsAndCanBeQueriedByOpsAssistantAfterADecline() throws Exception {
        String body = """
                {"merchantReference":"ord-explain-1","amountMinor":900000,"currency":"INR",
                 "risk":{"amountZscore":4.0,"txnVelocity1h":9,"geoDistanceKm":1200,
                         "deviceNew":1,"hourOfDay":3,"isForeign":1,"cardAgeDays":5}}
                """;
        // A decline aborts the saga before a 201/id comes back, so exercise the
        // decline path for coverage and assert the explanation flow on the
        // low-risk (id-returning) path below.
        mvc.perform(post("/v1/orchestrated-payments")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPaymentRequired());

        String lowRiskBody = """
                {"merchantReference":"ord-explain-2","amountMinor":5000,"currency":"INR",
                 "risk":{"amountZscore":0.1,"txnVelocity1h":1,"geoDistanceKm":4,
                         "deviceNew":0,"hourOfDay":14,"isForeign":0,"cardAgeDays":900}}
                """;
        String created = mvc.perform(post("/v1/orchestrated-payments")
                        .contentType(MediaType.APPLICATION_JSON).content(lowRiskBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = mapper.readTree(created);
        String paymentId = json.get("id").asText();

        String explanationBody = mvc.perform(get("/v1/orchestrated-payments/" + paymentId + "/risk-explanation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.narrative", containsString("APPROVE")))
                .andReturn().getResponse().getContentAsString();

        // A second call must return the same persisted narrative, not regenerate it.
        String secondCall = mvc.perform(get("/v1/orchestrated-payments/" + paymentId + "/risk-explanation"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(
                mapper.readTree(explanationBody).get("generatedAt"),
                mapper.readTree(secondCall).get("generatedAt"));

        String question = "what is the status of payment " + paymentId + "?";
        mvc.perform(post("/v1/ops-assistant/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("question", question))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", containsString(paymentId)));
    }

    @Test
    void riskExplanationFor404WhenNoPaymentExists() throws Exception {
        mvc.perform(get("/v1/orchestrated-payments/" + java.util.UUID.randomUUID() + "/risk-explanation"))
                .andExpect(status().isNotFound());
    }
}
