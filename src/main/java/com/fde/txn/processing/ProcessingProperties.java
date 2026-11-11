package com.fde.txn.processing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Map;

/**
 * Tunables for the background processor (see application.yml).
 *
 * @param maxAttempts   total attempts (first try + retries) before a transient failure becomes FAILED
 * @param baseBackoff   delay before the first retry; doubles on each further attempt
 * @param reorderWindow how long a brand-new account's events are buffered before its first sequence is fixed
 * @param gapAlertAfter how long an account may wait on a missing sequence before an alert is raised
 * @param fault         simulated transient failures, transactionId to number of failures
 */
@ConfigurationProperties("processing")
public record ProcessingProperties(
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("200ms") Duration baseBackoff,
        @DefaultValue("5s") Duration reorderWindow,
        @DefaultValue("5m") Duration gapAlertAfter,
        @DefaultValue Fault fault) {

    public record Fault(Map<String, Integer> transientFailures) {
        public Map<String, Integer> transientFailures() {
            return transientFailures == null ? Map.of() : transientFailures;
        }
    }

    /** Exponential backoff: base * 2^(attempt-1). */
    public Duration backoffFor(int attempt) {
        long factor = 1L << Math.min(Math.max(attempt - 1, 0), 16);
        return baseBackoff.multipliedBy(factor);
    }
}
