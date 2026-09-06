package com.ledgerflow.orchestration.saga;

/** Raised when a saga aborts. Carries the step that failed for diagnostics. */
public class SagaException extends RuntimeException {
    private final String failedStep;

    public SagaException(String failedStep, String message, Throwable cause) {
        super(message, cause);
        this.failedStep = failedStep;
    }

    public String getFailedStep() { return failedStep; }
}
