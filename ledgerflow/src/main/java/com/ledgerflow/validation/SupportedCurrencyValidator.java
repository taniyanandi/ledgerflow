package com.ledgerflow.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Currency;

/**
 * Checks the value against the JVM's ISO 4217 currency set (no external deps).
 * Two rules beyond "does Currency.getInstance throw":
 *  - the input must already be an uppercase 3-letter code ("usd" is rejected so
 *    clients normalize their input),
 *  - XXX (the "no currency" transaction-fund code) and other such placeholder
 *    entries are not valid payment currencies.
 */
public class SupportedCurrencyValidator implements ConstraintValidator<SupportedCurrency, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // presence is @NotBlank's job
        }
        if (!value.matches("[A-Z]{3}")) {
            return false;
        }
        try {
            Currency currency = Currency.getInstance(value);
            return !"XXX".equals(currency.getCurrencyCode());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
