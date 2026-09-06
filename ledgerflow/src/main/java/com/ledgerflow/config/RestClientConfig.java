package com.ledgerflow.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    /** Short timeouts so a slow fraud service degrades to the fallback quickly. */
    @Bean
    public RestClientCustomizer restClientCustomizer() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
            factory.setReadTimeout((int) Duration.ofSeconds(3).toMillis());
            builder.requestFactory(factory);
        };
    }
}
