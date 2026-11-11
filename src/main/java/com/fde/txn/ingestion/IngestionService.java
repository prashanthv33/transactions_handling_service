package com.fde.txn.ingestion;

import com.fde.txn.domain.DeliveryOutcome;
import com.fde.txn.domain.PayloadHasher;
import com.fde.txn.domain.RawTransaction;
import com.fde.txn.domain.TransactionRequest;
import com.fde.txn.domain.TransactionStatus;
import com.fde.txn.domain.TransactionValidator;
import com.fde.txn.domain.ValidationResult;
import com.fde.txn.persistence.DeliveryLog;
import com.fde.txn.persistence.DeliveryLogRepository;
import com.fde.txn.persistence.TransactionRecord;
import com.fde.txn.persistence.TransactionRecordRepository;
import com.fde.txn.reporting.LogMasker;
import com.fde.txn.reporting.TxnMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Idempotent intake. Every delivery is validated, fingerprinted and either stored as a new transaction or
 * classified as DUPLICATE / CONFLICT against the existing record with the same transactionId.
 * Intake never applies balances; that is the background processor's job.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final int MAX_INSERT_RACE_RETRIES = 3;

    private final TransactionValidator validator;
    private final PayloadHasher hasher;
    private final TransactionRecordRepository records;
    private final DeliveryLogRepository deliveries;
    private final TransactionTemplate tx;
    private final TxnMetrics metrics;
    private final Clock clock;

    public IngestionService(TransactionValidator validator, PayloadHasher hasher,
                            TransactionRecordRepository records, DeliveryLogRepository deliveries,
                            PlatformTransactionManager txManager, TxnMetrics metrics, Clock clock) {
        this.validator = validator;
        this.hasher = hasher;
        this.records = records;
        this.deliveries = deliveries;
        this.tx = new TransactionTemplate(txManager);
        this.metrics = metrics;
        this.clock = clock;
    }

    public IngestionResult ingest(RawTransaction raw) {
        ValidationResult validation = validator.validate(raw);
        String requestId = safeId(raw.requestId());
        String transactionId = validation.transactionId();
        try {
            MDC.put("transactionId", transactionId);
            MDC.put("requestId", requestId);

            if (transactionId == null) {
                // Not identifiable: we cannot store it as a transaction, only audit the delivery.
                return record(new IngestionResult(requestId, null, DeliveryOutcome.REJECTED, null,
                        validation.errorSummary()));
            }

            String hash = validation.valid() ? hasher.hash(validation.request()) : hasher.hashRaw(raw);

            // Insert-or-classify. A concurrent insert of the same transactionId makes the PK constraint fail;
            // we then re-read and classify against the winner. The database is the final arbiter.
            DataAccessException last = null;
            for (int attempt = 1; attempt <= MAX_INSERT_RACE_RETRIES; attempt++) {
                try {
                    return tx.execute(status -> insertOrClassify(validation, requestId, hash));
                } catch (DataAccessException e) {
                    last = e;
                    log.debug("event=ingest.race transactionId={} attempt={}", transactionId, attempt);
                }
            }
            throw last;
        } finally {
            MDC.remove("transactionId");
            MDC.remove("requestId");
        }
    }

    private IngestionResult insertOrClassify(ValidationResult v, String requestId, String hash) {
        Optional<TransactionRecord> existing = records.findById(v.transactionId());
        if (existing.isPresent()) {
            TransactionRecord rec = existing.get();
            if (rec.getPayloadHash().equals(hash)) {
                return record(new IngestionResult(requestId, rec.getTransactionId(), DeliveryOutcome.DUPLICATE,
                        rec.getStatus(), "Already received with an identical payload"));
            }
            return record(new IngestionResult(requestId, rec.getTransactionId(), DeliveryOutcome.CONFLICT,
                    rec.getStatus(), "transactionId already used with a different payload; not applied"));
        }

        Instant now = clock.instant();
        TransactionRecord rec;
        IngestionResult result;
        if (v.valid()) {
            TransactionRequest r = v.request();
            rec = new TransactionRecord(r.transactionId(), hash, TransactionStatus.RECEIVED, now);
            rec.setRequestId(r.requestId());
            rec.setAccountId(r.accountId());
            rec.setType(r.type());
            rec.setAmount(r.amount());
            rec.setCurrency(r.currency());
            rec.setSequenceNumber(r.sequenceNumber());
            rec.setEventTime(r.eventTime());
            rec.setOriginalTransactionId(r.originalTransactionId());
            result = new IngestionResult(requestId, r.transactionId(), DeliveryOutcome.ACCEPTED,
                    TransactionStatus.RECEIVED, null);
        } else {
            // Keep the identifiable parts so a rejected record still consumes its sequence slot.
            rec = new TransactionRecord(v.transactionId(), hash, TransactionStatus.REJECTED, now);
            rec.setRequestId(requestId);
            rec.setAccountId(v.accountId());
            rec.setSequenceNumber(v.sequenceNumber());
            rec.setLastError(truncate(v.errorSummary()));
            result = new IngestionResult(requestId, v.transactionId(), DeliveryOutcome.REJECTED,
                    TransactionStatus.REJECTED, v.errorSummary());
        }
        records.saveAndFlush(rec);
        if (v.valid()) {
            log.info("event=txn.received transactionId={} account={} seq={}",
                    rec.getTransactionId(), LogMasker.account(rec.getAccountId()), rec.getSequenceNumber());
        }
        return record(result);
    }

    /** Writes the delivery audit row, emits the metric and logs the outcome. */
    private IngestionResult record(IngestionResult result) {
        deliveries.save(new DeliveryLog(result.requestId(), result.transactionId(), result.outcome(),
                truncate(result.reason()), clock.instant()));
        metrics.delivery(result.outcome());
        switch (result.outcome()) {
            case DUPLICATE -> log.info("event=delivery.duplicate transactionId={} requestId={}",
                    result.transactionId(), result.requestId());
            case CONFLICT -> log.warn("event=delivery.conflict transactionId={} requestId={}",
                    result.transactionId(), result.requestId());
            case REJECTED -> log.warn("event=delivery.rejected transactionId={} requestId={} reason=\"{}\"",
                    result.transactionId(), result.requestId(), result.reason());
            default -> { }
        }
        return result;
    }

    private static String safeId(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.length() > 64 ? v.substring(0, 64) : v;
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 512 ? value : value.substring(0, 512);
    }
}
