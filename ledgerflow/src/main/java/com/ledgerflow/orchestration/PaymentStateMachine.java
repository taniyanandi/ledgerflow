package com.ledgerflow.orchestration;

import com.ledgerflow.domain.Enums.PaymentStatus;
import com.ledgerflow.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.ledgerflow.domain.Enums.PaymentStatus.*;

/**
 * The single authority on which payment status transitions are legal. Encoding
 * the lifecycle explicitly — rather than scattering {@code if (status == ...)}
 * checks across services — means an illegal transition (e.g. refunding a payment
 * that was never captured) is impossible to express, not merely discouraged.
 */
@Component
public class PaymentStateMachine {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED =
            new EnumMap<>(PaymentStatus.class);

    static {
        ALLOWED.put(INITIATED, EnumSet.of(AUTHORIZED, FAILED));
        ALLOWED.put(AUTHORIZED, EnumSet.of(CAPTURED, FAILED));
        ALLOWED.put(CAPTURED, EnumSet.of(REFUNDED));
        ALLOWED.put(FAILED, EnumSet.noneOf(PaymentStatus.class));
        ALLOWED.put(REFUNDED, EnumSet.noneOf(PaymentStatus.class));
    }

    public boolean canTransition(PaymentStatus from, PaymentStatus to) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(PaymentStatus.class)).contains(to);
    }

    public void assertCanTransition(PaymentStatus from, PaymentStatus to) {
        if (!canTransition(from, to)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Illegal payment transition: " + from + " -> " + to);
        }
    }
}
