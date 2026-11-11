package com.fde.txn.ingestion;

import com.fde.txn.domain.DeliveryOutcome;
import com.fde.txn.domain.TransactionStatus;

/** What happened to one delivery, as returned to the caller. */
public record IngestionResult(
        String requestId,
        String transactionId,
        DeliveryOutcome outcome,
        TransactionStatus transactionStatus,
        String reason) {
}
