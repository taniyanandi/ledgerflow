package com.ledgerflow.orchestration.saga;

/**
 * One step in a saga. {@link #execute} performs the forward action; if a later
 * step fails, {@link #compensate} is invoked to undo this step's effect. Together
 * these give us atomicity across services without a distributed transaction.
 *
 * @param <C> the mutable context threaded through every step of the saga
 */
public interface SagaStep<C> {

    String name();

    void execute(C context);

    /** Undo this step. Default no-op for steps with no side effect to reverse. */
    default void compensate(C context) {
        // nothing to undo by default
    }
}
