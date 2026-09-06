package com.ledgerflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ledgerFlowOpenApi() {
        return new OpenAPI().info(new Info()
                .title("LedgerFlow API")
                .version("0.1.0")
                .description("Payment orchestration platform — idempotent payment API "
                        + "backed by a double-entry ledger."));
    }
}
