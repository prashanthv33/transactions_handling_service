package com.fde.txn.reporting;

import com.fde.txn.domain.DeliveryOutcome;
import com.fde.txn.domain.TransactionStatus;
import com.fde.txn.domain.TransactionType;
import com.fde.txn.persistence.Account;
import com.fde.txn.persistence.DeliveryLog;
import com.fde.txn.persistence.LedgerEntry;
import com.fde.txn.persistence.TransactionRecord;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Read models returned by the API and the processing report. */
public final class Views {

    private Views() {
    }

    /** 100.0000 becomes 100 (never 1E+2), and 12.5000 becomes 12.5. */
    static BigDecimal plain(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    public record TransactionView(String transactionId, String requestId, String accountId, TransactionType type,
                                  BigDecimal amount, String currency, Long sequenceNumber,
                                  String originalTransactionId, TransactionStatus status, int attempts,
                                  String lastError, Instant receivedAt, Instant processedAt) {
        public static TransactionView from(TransactionRecord r) {
            return new TransactionView(r.getTransactionId(), r.getRequestId(), r.getAccountId(), r.getType(),
                    plain(r.getAmount()), r.getCurrency(),
                    r.getSequenceNumber(), r.getOriginalTransactionId(), r.getStatus(), r.getAttempts(),
                    r.getLastError(), r.getReceivedAt(), r.getProcessedAt());
        }
    }

    public record DeliveryView(String requestId, String transactionId, DeliveryOutcome outcome, String reason) {
        public static DeliveryView from(DeliveryLog d) {
            return new DeliveryView(d.getRequestId(), d.getTransactionId(), d.getOutcome(), d.getReason());
        }
    }

    public record LedgerView(String transactionId, BigDecimal amount, BigDecimal balanceAfter, String reversalOf,
                             Instant createdAt) {
        public static LedgerView from(LedgerEntry e) {
            return new LedgerView(e.getTransactionId(), plain(e.getAmount()),
                    plain(e.getBalanceAfter()), e.getReversalOf(), e.getCreatedAt());
        }
    }

    public record AccountView(String accountId, String currency, BigDecimal balance, long lastAppliedSeq,
                              List<LedgerView> ledger) {
        public static AccountView from(Account a, List<LedgerView> ledger) {
            return new AccountView(a.getAccountId(), a.getCurrency(), plain(a.getBalance()),
                    a.getLastAppliedSeq(), ledger);
        }
    }

    public record FailureView(String transactionId, String reason) {
    }

    public record GapView(String accountId, List<Long> missingSequences, List<String> waitingTransactions,
                          String note) {
    }
}
