package com.fde.txn.reporting;

import com.fde.txn.reporting.Views.AccountView;
import com.fde.txn.reporting.Views.DeliveryView;
import com.fde.txn.reporting.Views.FailureView;
import com.fde.txn.reporting.Views.GapView;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Operational snapshot: counts by status and outcome, plus everything an operator needs to act on. */
public record ProcessingSummary(
        Instant generatedAt,
        long totalDeliveries,
        Map<String, Long> deliveriesByOutcome,
        long totalTransactions,
        Map<String, Long> transactionsByStatus,
        long retriedTransactions,
        long totalRetryAttempts,
        long ledgerEntries,
        List<DeliveryView> duplicates,
        List<DeliveryView> conflicts,
        List<DeliveryView> rejected,
        List<FailureView> failed,
        List<GapView> sequenceGaps,
        List<AccountView> accounts) {
}
