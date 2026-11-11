package com.fde.txn.processing;

import com.fde.txn.domain.BusinessRuleException;
import com.fde.txn.domain.TransactionStatus;
import com.fde.txn.domain.TransactionType;
import com.fde.txn.persistence.Account;
import com.fde.txn.persistence.AccountRepository;
import com.fde.txn.persistence.LedgerEntry;
import com.fde.txn.persistence.LedgerEntryRepository;
import com.fde.txn.persistence.TransactionRecord;
import com.fde.txn.persistence.TransactionRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

/**
 * Applies one transaction atomically: ledger entry + balance + sequence cursor + status=PROCESSED commit
 * together or not at all. Any exception rolls the whole unit back, which makes retries safe.
 */
@Service
public class LedgerService {

    private final TransactionRecordRepository records;
    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final FaultInjector faultInjector;
    private final Clock clock;

    public LedgerService(TransactionRecordRepository records, AccountRepository accounts,
                         LedgerEntryRepository ledger, FaultInjector faultInjector, Clock clock) {
        this.records = records;
        this.accounts = accounts;
        this.ledger = ledger;
        this.faultInjector = faultInjector;
        this.clock = clock;
    }

    @Transactional
    public void apply(String transactionId) {
        TransactionRecord rec = records.findById(transactionId)
                .orElseThrow(() -> new IllegalStateException("Unknown transaction " + transactionId));
        if (rec.getStatus() != TransactionStatus.PROCESSING) {
            throw new IllegalStateException("Transaction " + transactionId + " is not claimed for processing");
        }
        Instant now = clock.instant();

        // Defensive idempotency: if an effect already exists, never apply it again.
        if (ledger.existsByTransactionId(transactionId)) {
            markProcessed(rec, now);
            return;
        }

        Account account = accounts.findById(rec.getAccountId())
                .orElseGet(() -> new Account(rec.getAccountId(), rec.getCurrency()));
        if (!account.getCurrency().equals(rec.getCurrency())) {
            throw new BusinessRuleException("CURRENCY_MISMATCH",
                    "Transaction currency does not match the account currency");
        }

        String reversalOf = null;
        BigDecimal delta;
        if (rec.getType() == TransactionType.CREDIT) {
            delta = rec.getAmount();
        } else if (rec.getType() == TransactionType.DEBIT) {
            delta = rec.getAmount().negate();
        } else {
            reversalOf = rec.getOriginalTransactionId();
            delta = reversalDelta(rec);
        }

        BigDecimal newBalance = account.getBalance().add(delta);
        if (newBalance.signum() < 0) {
            throw new BusinessRuleException("INSUFFICIENT_FUNDS", "Insufficient funds; overdraft is not permitted");
        }

        account.setBalance(newBalance);
        account.setLastAppliedSeq(Math.max(account.getLastAppliedSeq(), rec.getSequenceNumber()));
        accounts.save(account);
        ledger.save(new LedgerEntry(transactionId, rec.getAccountId(), delta, newBalance, reversalOf, now));
        markProcessed(rec, now);

        // Flush so that constraint violations surface here, then give the fault injector its chance to fail
        // after all writes have been issued but before commit.
        ledger.flush();
        faultInjector.beforeCommit(transactionId);
    }

    private BigDecimal reversalDelta(TransactionRecord rec) {
        TransactionRecord original = records.findById(rec.getOriginalTransactionId())
                .orElseThrow(() -> new BusinessRuleException("REVERSAL_ORIGINAL_NOT_FOUND",
                        "Original transaction not found"));
        if (original.getStatus() != TransactionStatus.PROCESSED) {
            throw new BusinessRuleException("REVERSAL_ORIGINAL_NOT_PROCESSED",
                    "Original transaction has not been processed");
        }
        if (original.getType() == TransactionType.REVERSAL) {
            throw new BusinessRuleException("REVERSAL_OF_REVERSAL", "A reversal cannot be reversed");
        }
        if (!original.getAccountId().equals(rec.getAccountId())) {
            throw new BusinessRuleException("REVERSAL_ACCOUNT_MISMATCH",
                    "Reversal must be on the same account as the original");
        }
        if (original.getAmount().compareTo(rec.getAmount()) != 0) {
            throw new BusinessRuleException("REVERSAL_AMOUNT_MISMATCH",
                    "Only full reversals are supported; amount must equal the original");
        }
        if (ledger.existsByReversalOf(original.getTransactionId())) {
            throw new BusinessRuleException("ALREADY_REVERSED", "Original transaction is already reversed");
        }
        LedgerEntry originalEntry = ledger.findByTransactionId(original.getTransactionId())
                .orElseThrow(() -> new IllegalStateException("Processed transaction without a ledger entry"));
        return originalEntry.getAmount().negate();
    }

    private static void markProcessed(TransactionRecord rec, Instant now) {
        rec.setStatus(TransactionStatus.PROCESSED);
        rec.setProcessedAt(now);
        rec.setNextAttemptAt(null);
        rec.setLastError(null);
    }
}
