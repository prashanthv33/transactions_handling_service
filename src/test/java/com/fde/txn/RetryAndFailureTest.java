package com.fde.txn;

import com.fde.txn.domain.TransactionStatus;
import com.fde.txn.persistence.TransactionRecord;
import com.fde.txn.support.IntegrationTestBase;
import com.fde.txn.support.Txn;
import org.junit.jupiter.api.Test;

import static com.fde.txn.domain.TransactionStatus.FAILED;
import static com.fde.txn.domain.TransactionStatus.PROCESSED;
import static com.fde.txn.domain.TransactionStatus.RETRY_PENDING;
import static org.assertj.core.api.Assertions.assertThat;

class RetryAndFailureTest extends IntegrationTestBase {

    @Test
    void transientFailureIsRetriedAndAppliedExactlyOnce() {
        faults.failNext("TXN-1", 2);
        ingestion.ingest(Txn.of("TXN-1", "REQ-1", "ACC-1", "CREDIT", "500", 1));

        processor.processOnce(true);
        assertThat(statusOf("TXN-1")).isEqualTo(RETRY_PENDING);
        // The fault fires AFTER the ledger and balance writes; rollback must leave no trace.
        assertThat(ledger.count()).isZero();
        assertThat(accounts.findById("ACC-1")).isEmpty();

        processor.drain();

        TransactionRecord rec = records.findById("TXN-1").orElseThrow();
        assertThat(rec.getStatus()).isEqualTo(PROCESSED);
        assertThat(rec.getAttempts()).isEqualTo(3);
        assertThat(ledger.countByTransactionId("TXN-1")).isEqualTo(1);
        assertThat(balance("ACC-1")).isEqualByComparingTo("500");
    }

    @Test
    void retryExhaustionMarksFailedAndTheAccountContinues() {
        faults.failNext("TXN-2", 10);
        ingestion.ingest(Txn.of("TXN-1", "REQ-1", "ACC-1", "CREDIT", "100", 1));
        ingestion.ingest(Txn.of("TXN-2", "REQ-2", "ACC-1", "CREDIT", "50", 2));
        ingestion.ingest(Txn.of("TXN-3", "REQ-3", "ACC-1", "DEBIT", "30", 3));

        processor.drain();

        TransactionRecord failed = records.findById("TXN-2").orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(FAILED);
        assertThat(failed.getAttempts()).isEqualTo(3);
        assertThat(failed.getLastError()).startsWith("RETRY_EXHAUSTED after 3 attempts");
        assertThat(statusOf("TXN-3")).isEqualTo(PROCESSED);
        assertThat(balance("ACC-1")).isEqualByComparingTo("70");
        assertThat(ledger.countByTransactionId("TXN-2")).isZero();
    }

    @Test
    void laterSequencesWaitWhileTheHeadIsRetrying() {
        faults.failNext("TXN-1", 1);
        ingestion.ingest(Txn.of("TXN-1", "REQ-1", "ACC-1", "CREDIT", "100", 1));
        ingestion.ingest(Txn.of("TXN-2", "REQ-2", "ACC-1", "DEBIT", "60", 2));

        processor.processOnce(true);

        assertThat(statusOf("TXN-1")).isEqualTo(RETRY_PENDING);
        assertThat(statusOf("TXN-2")).isNotEqualTo(PROCESSED); // would otherwise overdraw
        processor.drain();
        assertThat(balance("ACC-1")).isEqualByComparingTo("40");
    }

    @Test
    void overdraftIsAPermanentFailureAndNotRetried() {
        ingestion.ingest(Txn.of("TXN-1", "REQ-1", "ACC-1", "CREDIT", "10", 1));
        ingestion.ingest(Txn.of("TXN-2", "REQ-2", "ACC-1", "DEBIT", "50", 2));
        ingestion.ingest(Txn.of("TXN-3", "REQ-3", "ACC-1", "CREDIT", "5", 3));

        processor.drain();

        TransactionRecord rec = records.findById("TXN-2").orElseThrow();
        assertThat(rec.getStatus()).isEqualTo(FAILED);
        assertThat(rec.getAttempts()).isEqualTo(1);
        assertThat(rec.getLastError()).startsWith("INSUFFICIENT_FUNDS");
        assertThat(statusOf("TXN-3")).isEqualTo(PROCESSED);
        assertThat(balance("ACC-1")).isEqualByComparingTo("15");
    }

    @Test
    void currencyMismatchIsRejectedByBusinessRules() {
        ingestion.ingest(Txn.of("TXN-1", "REQ-1", "ACC-1", "CREDIT", "10", 1));
        ingestion.ingest(new com.fde.txn.domain.RawTransaction("TXN-2", "REQ-2", "ACC-1", "CREDIT", "10", "EUR",
                "2", null, null));
        processor.drain();

        assertThat(statusOf("TXN-2")).isEqualTo(FAILED);
        assertThat(records.findById("TXN-2").orElseThrow().getLastError()).startsWith("CURRENCY_MISMATCH");
    }

    @Test
    void operatorCanReplayAFailedTransactionOnce() {
        faults.failNext("TXN-1", 3);
        ingestion.ingest(Txn.of("TXN-1", "REQ-1", "ACC-1", "CREDIT", "100", 1));
        ingestion.ingest(Txn.of("TXN-2", "REQ-2", "ACC-1", "CREDIT", "1", 2));
        processor.drain();
        assertThat(statusOf("TXN-1")).isEqualTo(FAILED);

        assertThat(processor.replay("TXN-1")).isTrue();
        processor.drain();

        assertThat(statusOf("TXN-1")).isEqualTo(PROCESSED);
        assertThat(balance("ACC-1")).isEqualByComparingTo("101");
        assertThat(processor.replay("TXN-1")).isFalse(); // PROCESSED is never replayable
        assertThat(ledger.countByTransactionId("TXN-1")).isEqualTo(1);
    }

    @Test
    void recordInterruptedMidProcessingIsRecoveredOnStartup() {
        ingestion.ingest(Txn.of("TXN-1", "REQ-1", "ACC-1", "CREDIT", "100", 1));
        TransactionRecord rec = records.findById("TXN-1").orElseThrow();
        rec.setStatus(TransactionStatus.PROCESSING); // simulate a crash after claim, before commit
        records.save(rec);

        assertThat(processor.recoverInterrupted()).isEqualTo(1);
        processor.drain();

        assertThat(statusOf("TXN-1")).isEqualTo(PROCESSED);
        assertThat(ledger.count()).isEqualTo(1);
    }
}
