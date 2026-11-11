package com.fde.txn.processing;

import com.fde.txn.domain.BusinessRuleException;
import com.fde.txn.domain.TransactionStatus;
import com.fde.txn.domain.TransientProcessingException;
import com.fde.txn.persistence.Account;
import com.fde.txn.persistence.AccountRepository;
import com.fde.txn.persistence.TransactionRecord;
import com.fde.txn.persistence.TransactionRecordRepository;
import com.fde.txn.reporting.LogMasker;
import com.fde.txn.reporting.TxnMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * The background worker logic: per-account sequencing (Option A), claiming, applying, retrying and failing.
 *
 * <p>Ordering rule per account: the next record applied must have {@code sequenceNumber == expected}, where
 * expected is {@code lastAppliedSeq + 1}, skipping slots already consumed by REJECTED or FAILED records. Later
 * sequences wait as PENDING. A brand-new account has no cursor, so its records are buffered for a reorder window
 * and the account starts at the lowest sequence seen.</p>
 *
 * <p>Concurrency: a single worker runs at a time (lock). In production this maps to one consumer per account
 * partition. The database constraints and optimistic locking remain the final guard.</p>
 */
@Service
public class TransactionProcessor {

    private static final Logger log = LoggerFactory.getLogger(TransactionProcessor.class);

    private static final Set<TransactionStatus> ELIGIBLE =
            EnumSet.of(TransactionStatus.RECEIVED, TransactionStatus.PENDING, TransactionStatus.RETRY_PENDING);
    private static final Set<TransactionStatus> SLOT_CONSUMERS =
            EnumSet.of(TransactionStatus.REJECTED, TransactionStatus.FAILED);

    private enum Attempt { PROCESSED, FAILED, RETRY_SCHEDULED }

    private final TransactionRecordRepository records;
    private final AccountRepository accounts;
    private final LedgerService ledgerService;
    private final ProcessingProperties props;
    private final TxnMetrics metrics;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final ReentrantLock workerLock = new ReentrantLock();
    private final Map<String, Long> gapAlertsRaised = new ConcurrentHashMap<>();

    public TransactionProcessor(TransactionRecordRepository records, AccountRepository accounts,
                                LedgerService ledgerService, ProcessingProperties props, TxnMetrics metrics,
                                Clock clock, PlatformTransactionManager txManager) {
        this.records = records;
        this.accounts = accounts;
        this.ledgerService = ledgerService;
        this.props = props;
        this.metrics = metrics;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * One pass over all eligible work.
     *
     * @param reorderWindowClosed true when no more input is expected (end of a batch), so new accounts can be
     *                            sequenced immediately; false in streaming mode, where the time window applies
     * @return number of processing attempts made (0 means there was nothing actionable)
     */
    public int processOnce(boolean reorderWindowClosed) {
        workerLock.lock();
        try {
            Instant now = clock.instant();
            Map<String, List<TransactionRecord>> byAccount = records.findByStatusIn(ELIGIBLE).stream()
                    .collect(Collectors.groupingBy(TransactionRecord::getAccountId, TreeMap::new,
                            Collectors.toCollection(ArrayList::new)));
            int attempts = 0;
            for (Map.Entry<String, List<TransactionRecord>> e : byAccount.entrySet()) {
                attempts += processAccount(e.getKey(), e.getValue(), now, reorderWindowClosed);
            }
            return attempts;
        } finally {
            workerLock.unlock();
        }
    }

    /**
     * Processes until no actionable work remains, waiting for retry backoffs. PENDING records blocked by a
     * genuine sequence gap are left in place and reported.
     */
    public void drain() {
        while (true) {
            if (processOnce(true) > 0) {
                continue;
            }
            Optional<Instant> nextRetry = records.findEarliestNextAttempt(TransactionStatus.RETRY_PENDING);
            if (nextRetry.isEmpty()) {
                return;
            }
            Duration wait = Duration.between(clock.instant(), nextRetry.get());
            if (wait.isNegative() || wait.isZero()) {
                return; // due retries that were not actionable are blocked behind a gap
            }
            sleep(wait);
        }
    }

    private int processAccount(String accountId, List<TransactionRecord> recs, Instant now, boolean windowClosed) {
        recs.sort(Comparator.comparing(TransactionRecord::getSequenceNumber)
                .thenComparing(TransactionRecord::getReceivedAt)
                .thenComparing(TransactionRecord::getTransactionId));

        // Slots consumed without an effect. A REJECTED record only consumes its slot when no valid record claims
        // the same sequence: a valid transaction always takes precedence over unusable input.
        Set<Long> claimed = recs.stream().map(TransactionRecord::getSequenceNumber).collect(Collectors.toSet());
        Set<Long> consumed = records.findByAccountIdAndStatusInAndSequenceNumberNotNull(accountId, SLOT_CONSUMERS)
                .stream()
                .filter(r -> r.getStatus() == TransactionStatus.FAILED || !claimed.contains(r.getSequenceNumber()))
                .map(TransactionRecord::getSequenceNumber)
                .collect(Collectors.toCollection(HashSet::new));

        Optional<Account> account = accounts.findById(accountId);
        long expected;
        if (account.isPresent()) {
            expected = account.get().getLastAppliedSeq() + 1;
        } else {
            Instant oldest = recs.stream().map(TransactionRecord::getReceivedAt).min(Comparator.naturalOrder())
                    .orElse(now);
            if (!windowClosed && oldest.plus(props.reorderWindow()).isAfter(now)) {
                markPending(recs);
                return 0;
            }
            long minSeq = recs.get(0).getSequenceNumber();
            for (Long s : consumed) {
                minSeq = Math.min(minSeq, s);
            }
            expected = minSeq;
        }

        int attempts = 0;
        for (int i = 0; i < recs.size(); i++) {
            TransactionRecord rec = recs.get(i);
            while (consumed.contains(expected)) {
                expected++;
            }
            long seq = rec.getSequenceNumber();

            if (seq < expected) {
                if (rec.isManualReplay()) {
                    if (isBackingOff(rec, now)) {
                        continue;
                    }
                    attempt(rec, now); // operator-approved replay behind the cursor
                } else {
                    fail(rec.getTransactionId(),
                            "SEQUENCE_ALREADY_CONSUMED: sequence " + seq + " was already used for this account");
                }
                attempts++;
                continue;
            }
            if (seq > expected) {
                markPending(recs.subList(i, recs.size()));
                raiseGapAlertIfStale(accountId, expected, rec, now);
                break;
            }
            if (isBackingOff(rec, now)) {
                break; // head of line is backing off; later sequences must wait to preserve order
            }

            Attempt result = attempt(rec, now);
            attempts++;
            if (result == Attempt.PROCESSED) {
                expected = seq + 1;
            } else if (result == Attempt.FAILED) {
                consumed.add(seq);
            } else {
                break;
            }
        }
        return attempts;
    }

    private static boolean isBackingOff(TransactionRecord rec, Instant now) {
        return rec.getStatus() == TransactionStatus.RETRY_PENDING
                && rec.getNextAttemptAt() != null && rec.getNextAttemptAt().isAfter(now);
    }

    private Attempt attempt(TransactionRecord rec, Instant now) {
        String id = rec.getTransactionId();
        int attemptNo = claim(id);
        MDC.put("transactionId", id);
        MDC.put("attempt", Integer.toString(attemptNo));
        try {
            ledgerService.apply(id);
            metrics.processing("PROCESSED");
            log.info("event=txn.processed transactionId={} account={} seq={} attempt={}",
                    id, LogMasker.account(rec.getAccountId()), rec.getSequenceNumber(), attemptNo);
            return Attempt.PROCESSED;
        } catch (BusinessRuleException e) {
            fail(id, e.getCode() + ": " + e.getMessage());
            return Attempt.FAILED;
        } catch (DataIntegrityViolationException e) {
            fail(id, "CONSTRAINT_VIOLATION: duplicate effect prevented by a database constraint");
            return Attempt.FAILED;
        } catch (RuntimeException e) {
            // Everything else is treated as transient, bounded by maxAttempts.
            String reason = e instanceof TransientProcessingException ? e.getMessage() : e.getClass().getSimpleName();
            if (attemptNo >= props.maxAttempts()) {
                fail(id, "RETRY_EXHAUSTED after " + attemptNo + " attempts: " + reason);
                return Attempt.FAILED;
            }
            scheduleRetry(id, now.plus(props.backoffFor(attemptNo)), reason, attemptNo);
            return Attempt.RETRY_SCHEDULED;
        } finally {
            MDC.remove("transactionId");
            MDC.remove("attempt");
        }
    }

    /** Marks the record PROCESSING and counts the attempt in its own short transaction. */
    private int claim(String id) {
        return tx.execute(s -> {
            TransactionRecord r = records.findById(id).orElseThrow();
            r.setStatus(TransactionStatus.PROCESSING);
            r.setAttempts(r.getAttempts() + 1);
            r.setNextAttemptAt(null);
            return r.getAttempts();
        });
    }

    private void scheduleRetry(String id, Instant nextAttemptAt, String reason, int attemptNo) {
        tx.executeWithoutResult(s -> {
            TransactionRecord r = records.findById(id).orElseThrow();
            r.setStatus(TransactionStatus.RETRY_PENDING);
            r.setNextAttemptAt(nextAttemptAt);
            r.setLastError(reason);
        });
        metrics.processing("RETRY_SCHEDULED");
        log.warn("event=txn.retry_scheduled transactionId={} attempt={} nextAttemptAt={} reason=\"{}\"",
                id, attemptNo, nextAttemptAt, reason);
    }

    private void fail(String id, String reason) {
        tx.executeWithoutResult(s -> {
            TransactionRecord r = records.findById(id).orElseThrow();
            r.setStatus(TransactionStatus.FAILED);
            r.setLastError(reason.length() > 512 ? reason.substring(0, 512) : reason);
            r.setNextAttemptAt(null);
            r.setManualReplay(false);
        });
        metrics.processing("FAILED");
        log.warn("event=txn.failed transactionId={} reason=\"{}\"", id, reason);
    }

    private void markPending(List<TransactionRecord> recs) {
        List<String> ids = recs.stream()
                .filter(r -> r.getStatus() == TransactionStatus.RECEIVED)
                .map(TransactionRecord::getTransactionId)
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        tx.executeWithoutResult(s -> records.findAllById(ids).forEach(r -> {
            if (r.getStatus() == TransactionStatus.RECEIVED) {
                r.setStatus(TransactionStatus.PENDING);
            }
        }));
        ids.forEach(id -> log.info("event=txn.pending transactionId={}", id));
    }

    private void raiseGapAlertIfStale(String accountId, long missingSeq, TransactionRecord waiting, Instant now) {
        if (waiting.getReceivedAt().plus(props.gapAlertAfter()).isAfter(now)) {
            return;
        }
        Long previous = gapAlertsRaised.put(accountId, missingSeq);
        if (previous == null || previous != missingSeq) {
            metrics.gapAlert();
            log.error("event=sequence.gap.alert account={} missingSeq={} waitingSince={}",
                    LogMasker.account(accountId), missingSeq, waiting.getReceivedAt());
        }
    }

    /** Operator action: re-queue a FAILED transaction. Returns false if it is not in a replayable state. */
    public boolean replay(String transactionId) {
        Boolean ok = tx.execute(s -> records.findById(transactionId)
                .filter(r -> r.getStatus() == TransactionStatus.FAILED && r.getType() != null)
                .map(r -> {
                    r.setStatus(TransactionStatus.RETRY_PENDING);
                    r.setAttempts(0);
                    r.setManualReplay(true);
                    r.setNextAttemptAt(clock.instant());
                    r.setLastError("Replay requested by operator");
                    return true;
                })
                .orElse(false));
        if (Boolean.TRUE.equals(ok)) {
            log.info("event=txn.replay_requested transactionId={}", transactionId);
        }
        return Boolean.TRUE.equals(ok);
    }

    /** Crash recovery: a record left in PROCESSING was interrupted mid-apply (rolled back), so retry it. */
    public int recoverInterrupted() {
        Integer count = tx.execute(s -> {
            List<TransactionRecord> stuck = records.findByStatus(TransactionStatus.PROCESSING);
            stuck.forEach(r -> {
                r.setStatus(TransactionStatus.RETRY_PENDING);
                r.setNextAttemptAt(clock.instant());
                r.setLastError("Recovered after interrupted processing");
            });
            return stuck.size();
        });
        if (count != null && count > 0) {
            log.warn("event=txn.recovered_interrupted count={}", count);
        }
        return count == null ? 0 : count;
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(Math.min(d.toMillis() + 1, 5_000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
