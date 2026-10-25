
package com.ledgerflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerflow.api.OrchestratedPaymentController;
import com.ledgerflow.api.dto.OrchestratedPaymentRequest;
import com.ledgerflow.exception.GlobalExceptionHandler;
import com.ledgerflow.orchestration.PaymentOrchestrationService;
import com.ledgerflow.repository.PaymentEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;

class OrchestratedPaymentValidationTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        PaymentOrchestrationService orchestration =
                mock(PaymentOrchestrationService.class);
        PaymentEventRepository eventRepository =
                mock(PaymentEventRepository.class);

        mvc = MockMvcBuilders
                .standaloneSetup(new OrchestratedPaymentController(
                        orchestration, eventRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void invalidPaymentRequestReturnsBadRequestWithFieldErrors() throws Exception {
        String body = """
                {"merchantReference":"","amountMinor":-100,"currency":"inr"}
                """;

        mvc.perform(post("/v1/orchestrated-payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(allOf(
                        containsString("merchantReference"),
                        containsString("amountMinor"),
                        containsString("currency")
                )));
    }
}
