package com.ledgerflow.orchestration.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Executes an ordered list of saga steps. If any step throws, every already-completed
 * step is compensated in strict reverse order (LIFO), then a {@link SagaException} is
 * raised. Compensation failures are logged but never mask the original cause — a
 * partially-compensated saga must be visible, not swallowed.
 */
public class SagaExecutor<C> {

    private static final Logger log = LoggerFactory.getLogger(SagaExecutor.class);

    private final List<SagaStep<C>> steps;

    public SagaExecutor(List<SagaStep<C>> steps) {
        this.steps = steps;
    }

    public void run(C context) {
        Deque<SagaStep<C>> completed = new ArrayDeque<>();
        for (SagaStep<C> step : steps) {
            try {
                log.debug("Saga step executing: {}", step.name());
                step.execute(context);
                completed.push(step);
            } catch (RuntimeException ex) {
                log.warn("Saga step '{}' failed: {} — compensating {} completed step(s)",
                        step.name(), ex.getMessage(), completed.size());
                compensate(completed, context);
                throw new SagaException(step.name(),
                        "Saga aborted at step '" + step.name() + "'", ex);
            }
        }
    }

    private void compensate(Deque<SagaStep<C>> completed, C context) {
        while (!completed.isEmpty()) {
            SagaStep<C> step = completed.pop();
            try {
                step.compensate(context);
                log.debug("Compensated step: {}", step.name());
            } catch (RuntimeException comp) {
                log.error("Compensation for step '{}' FAILED — manual reconciliation needed",
                        step.name(), comp);
            }
        }
    }
}
