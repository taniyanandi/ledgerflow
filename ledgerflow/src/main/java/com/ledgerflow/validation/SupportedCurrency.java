package com.ledgerflow.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a String is an ISO 4217 currency code known to the JVM
 * (checked against {@link java.util.Currency#getAvailableCurrencies()}).
 * Accepts only 3-letter uppercase codes that actually exist — "XYZ" or
 * "AAA" pass a plain {@code @Pattern("[A-Z]{3}")} but fail here.
 */
@Documented
@Constraint(validatedBy = SupportedCurrencyValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SupportedCurrency {
    String message() default "currency must be a valid ISO 4217 code";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
