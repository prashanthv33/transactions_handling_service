package com.fde.txn.reporting;

import com.fde.txn.ingestion.IngestionResult;
import com.fde.txn.reporting.Views.TransactionView;

import java.util.List;
import java.util.Map;

/** Renders the processing report as plain text for the batch CLI. */
public final class ReportFormatter {

    private static final String LINE = "-".repeat(100);

    private ReportFormatter() {
    }

    public static String ingestion(List<IngestionResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append(LINE).append('\n').append("INGESTION (in arrival order)\n").append(LINE).append('\n');
        sb.append(String.format("%-4s %-12s %-12s %-10s %s%n", "#", "requestId", "transaction", "outcome", "reason"));
        int i = 1;
        for (IngestionResult r : results) {
            sb.append(String.format("%-4d %-12s %-12s %-10s %s%n", i++, nz(r.requestId()), nz(r.transactionId()),
                    r.outcome(), nz(r.reason())));
        }
        return sb.toString();
    }

    public static String report(ProcessingSummary s, List<TransactionView> transactions) {
        StringBuilder sb = new StringBuilder();
        sb.append(LINE).append('\n').append("TRANSACTIONS (final state)\n").append(LINE).append('\n');
        sb.append(String.format("%-10s %-8s %-9s %4s %10s %-14s %3s  %s%n",
                "txn", "account", "type", "seq", "amount", "status", "att", "detail"));
        for (TransactionView t : transactions) {
            sb.append(String.format("%-10s %-8s %-9s %4s %10s %-14s %3d  %s%n",
                    t.transactionId(), nz(t.accountId()), t.type() == null ? "-" : t.type(),
                    t.sequenceNumber() == null ? "-" : t.sequenceNumber(),
                    t.amount() == null ? "-" : t.amount().toPlainString() + " " + t.currency(),
                    t.status(), t.attempts(), nz(t.lastError())));
        }

        sb.append(LINE).append('\n').append("SUMMARY\n").append(LINE).append('\n');
        sb.append("Deliveries received : ").append(s.totalDeliveries()).append("  ")
                .append(nonZero(s.deliveriesByOutcome())).append('\n');
        sb.append("Transactions        : ").append(s.totalTransactions()).append("  ")
                .append(nonZero(s.transactionsByStatus())).append('\n');
        sb.append("Retried             : ").append(s.retriedTransactions()).append(" transaction(s), ")
                .append(s.totalRetryAttempts()).append(" retry attempt(s)\n");
        sb.append("Ledger entries      : ").append(s.ledgerEntries()).append('\n');
        s.duplicates().forEach(d -> sb.append("Duplicate           : ").append(d.requestId())
                .append(" -> ").append(d.transactionId()).append('\n'));
        s.conflicts().forEach(d -> sb.append("Conflict            : ").append(d.requestId())
                .append(" -> ").append(d.transactionId()).append('\n'));
        s.rejected().forEach(d -> sb.append("Rejected            : ").append(nz(d.requestId())).append(" -> ")
                .append(nz(d.transactionId())).append(" (").append(d.reason()).append(")\n"));
        s.failed().forEach(f -> sb.append("Failed              : ").append(f.transactionId())
                .append(" (").append(f.reason()).append(")\n"));
        s.sequenceGaps().forEach(g -> sb.append("Sequence gap        : ").append(g.accountId())
                .append(" missing ").append(g.missingSequences()).append(", waiting ")
                .append(g.waitingTransactions()).append('\n'));
        sb.append("Balances            : ");
        s.accounts().forEach(a -> sb.append(a.accountId()).append('=').append(a.balance().toPlainString())
                .append(' ').append(a.currency()).append("  "));
        sb.append('\n').append(LINE).append('\n');
        return sb.toString();
    }

    private static String nonZero(Map<String, Long> counts) {
        StringBuilder sb = new StringBuilder("{");
        counts.forEach((k, v) -> {
            if (v > 0) {
                sb.append(sb.length() > 1 ? ", " : "").append(k).append('=').append(v);
            }
        });
        return sb.append('}').toString();
    }

    private static String nz(Object o) {
        return o == null ? "" : o.toString();
    }
}
