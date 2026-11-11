package com.fde.txn.domain;

/**
 * Lifecycle of a business transaction (one row per transactionId).
 * Per-delivery outcomes such as DUPLICATE / CONFLICT are tracked separately, see {@link DeliveryOutcome}.
 */
public enum TransactionStatus {
    /** Accepted and stored, not yet picked up by the worker. */
    RECEIVED,
    /** Waiting for an earlier sequence number (or the reorder window) before it can be applied. */
    PENDING,
    /** Claimed by the worker; an atomic apply is in flight. */
    PROCESSING,
    /** A transient failure occurred; will be retried after a backoff. */
    RETRY_PENDING,
    /** Applied exactly once to the ledger and balance. Terminal. */
    PROCESSED,
    /** Business rule failure or retries exhausted. Terminal, replayable by an operator. */
    FAILED,
    /** Failed input validation; never processed. Terminal. */
    REJECTED
}
