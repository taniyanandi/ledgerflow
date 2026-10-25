package com.ledgerflow.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Propagates a correlation ID for the current HTTP request so all log lines
 * produced while handling it on the request thread can be tied together
 * (controller &rarr; service &rarr; ledger).
 *
 * <p>If the caller sends an {@code X-Correlation-Id} header, that value is
 * used after validation (see below); otherwise a new UUID is generated. The
 * value is stored in the SLF4J {@link MDC} under {@value #MDC_KEY} (the
 * logging pattern in {@code application.yml} renders it via
 * {@code %X{correlationId}}), echoed back in the response header, and
 * removed from the MDC when the request finishes so it never leaks into
 * another request handled by the same thread.
 *
 * <p><b>Validation:</b> the incoming header is untrusted input that ends up
 * in log files and a response header, so it is only accepted when it is
 * non-blank, at most {@value #MAX_CORRELATION_ID_LENGTH} characters, and
 * matches {@code [A-Za-z0-9._-]+}. Anything else is replaced with a
 * generated UUID.
 *
 * <p><b>Scope:</b> the MDC is thread-local, so the correlation ID covers the
 * HTTP request thread only. It does <em>not</em> automatically propagate to
 * {@code @Async}/executor threads, Kafka producers/consumers, or scheduled
 * jobs; those would need explicit propagation.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    /** Maximum length accepted for a caller-supplied correlation ID. */
    static final int MAX_CORRELATION_ID_LENGTH = 64;

    private static final Pattern VALID_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]+");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request.getHeader(HEADER_NAME));

        try {
            MDC.put(MDC_KEY, correlationId);
            response.setHeader(HEADER_NAME, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String resolveCorrelationId(String incoming) {
        if (incoming == null
                || incoming.isBlank()
                || incoming.length() > MAX_CORRELATION_ID_LENGTH
                || !VALID_CORRELATION_ID.matcher(incoming).matches()) {
            return UUID.randomUUID().toString();
        }
        return incoming;
    }
}
