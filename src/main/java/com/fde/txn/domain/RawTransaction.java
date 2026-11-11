package com.fde.txn.domain;

/**
 * Untrusted transaction input exactly as received from a source (file, API, queue).
 * Every field is a nullable string so that malformed records can be validated and isolated
 * instead of failing deserialization for the whole batch.
 */
public record RawTransaction(
        String transactionId,
        String requestId,
        String accountId,
        String transactionType,
        String amount,
        String currency,
        String sequenceNumber,
        String eventTime,
        String originalTransactionId) {
}
