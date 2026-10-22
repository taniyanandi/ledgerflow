package com.ledgerflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for outbound webhook delivery retries.
 */
@ConfigurationProperties(prefix = "ledgerflow.webhook")
public class WebhookProperties {

    /** Maximum delivery attempts before a delivery is dead-lettered. */
    private int maxAttempts = 5;

    /** Base delay in seconds used by the retry backoff formula. */
    private long initialBackoffSeconds = 10;

    /** Exponent applied to the current attempt number in the backoff formula. */
    private double backoffMultiplier = 2.0;

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getInitialBackoffSeconds() {
        return initialBackoffSeconds;
    }

    public void setInitialBackoffSeconds(long initialBackoffSeconds) {
        this.initialBackoffSeconds = initialBackoffSeconds;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public void setBackoffMultiplier(double backoffMultiplier) {
        this.backoffMultiplier = backoffMultiplier;
    }
}
