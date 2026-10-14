package com.ledgerflow.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportedCurrencyValidatorTest {

    private static Validator validator;

    static class Bean {
        @SupportedCurrency String currency;
        Bean(String currency) { this.currency = currency; }
    }

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void acceptsRealIsoCodes() {
        for (String code : new String[]{"USD", "INR", "EUR", "JPY", "BRL"}) {
            assertTrue(validator.validate(new Bean(code)).isEmpty(), code + " should be accepted");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ZZZ", "AAA", "XYZ", "usd", "US", "USDD", "XXX", "   "})
    void rejectsFakeOrMalformedCodes(String code) {
        assertFalse(validator.validate(new Bean(code)).isEmpty(), code + " should be rejected");
    }

    @Test
    void nullIsLeftToNotBlank() {
        assertTrue(validator.validate(new Bean(null)).isEmpty());
    }
}
