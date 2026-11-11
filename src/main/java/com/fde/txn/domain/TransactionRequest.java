package com.fde.txn.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** A validated, strongly typed transaction request. Instances are produced by {@link TransactionValidator}. */
public record TransactionRequest(
        String transactionId,
        String requestId,
        String accountId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        long sequenceNumber,
        Instant eventTime,
        String originalTransactionId) {
}
