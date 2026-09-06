package com.ledgerflow;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end tests against a real PostgreSQL via Testcontainers — the same
 * database engine as production, so Flyway migrations and SQL constraints are
 * exercised for real, not mocked away.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@org.springframework.test.context.ActiveProfiles("test")
class PaymentApiIntegrationTest {

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

    private static final String BODY = """
            {"merchantReference":"order-42","amountMinor":150000,"currency":"INR"}
            """;

    @Test
    void createsPaymentAndPostsBalancedLedger() throws Exception {
        mvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", "key-create-1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("CAPTURED")))
                .andExpect(jsonPath("$.amountMinor", is(150000)));

        // Double-entry proof: the asset and the liability moved by the same amount.
        mvc.perform(get("/v1/accounts/ACQUIRER_CASH/balance"))
                .andExpect(jsonPath("$.balanceMinor", is(150000)));
        mvc.perform(get("/v1/accounts/MERCHANT_PAYABLE/balance"))
                .andExpect(jsonPath("$.balanceMinor", is(150000)));
    }

    @Test
    void sameIdempotencyKeyReplaysSameResponse() throws Exception {
        String first = mvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", "key-idem-2")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String second = mvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", "key-idem-2")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode a = mapper.readTree(first);
        JsonNode b = mapper.readTree(second);
        // Same payment id returned twice — the second call created nothing new.
        org.junit.jupiter.api.Assertions.assertEquals(a.get("id"), b.get("id"));
    }

    @Test
    void reusedKeyWithDifferentBodyIsRejected() throws Exception {
        mvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", "key-conflict-3")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());

        String differentBody = """
                {"merchantReference":"order-99","amountMinor":900,"currency":"INR"}
                """;
        mvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", "key-conflict-3")
                        .contentType(MediaType.APPLICATION_JSON).content(differentBody))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsMissingIdempotencyKey() throws Exception {
        mvc.perform(post("/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
    }
}
