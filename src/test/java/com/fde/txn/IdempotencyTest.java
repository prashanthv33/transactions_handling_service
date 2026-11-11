package com.fde.txn;

import com.fde.txn.domain.DeliveryOutcome;
import com.fde.txn.domain.RawTransaction;
import com.fde.txn.ingestion.IngestionResult;
import com.fde.txn.support.IntegrationTestBase;
import com.fde.txn.support.Txn;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.fde.txn.domain.TransactionStatus.PROCESSED;
import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyTest extends IntegrationTestBase {

    @Test
    void sameTransactionIdWithNewRequestIdIsADuplicate() {
        IngestionResult first = ingestion.ingest(Txn.of("TXN-1", "REQ-1", "ACC-1", "CREDIT", "100", 1));
        IngestionResult retry = ingestion.ingest(Txn.of("TXN-1", "REQ-1-RETRY", "ACC-1", "CREDIT", "100.00", 1));
        processor.drain();

        assertThat(first.outcome()).isEqualTo(DeliveryOutcome.ACCEPTED);
        assertThat(retry.outcome()).isEqualTo(DeliveryOutcome.DUPLICATE);
        assertThat(records.count()).isEqualTo(1);
        assertThat(ledger.count()).isEqualTo(1);
        assertThat(balance("ACC-1")).isEqualByComparingTo("100");
    }

    @Test
    void sameTransactionIdWithDifferentPayloadIsAConflictAndNeverApplied() {
        ingestion.ingest(Txn.of("TXN-1", "REQ-1", "ACC-1", "CREDIT", "100", 1));
        IngestionResult tampered = ingestion.ingest(Txn.of("TXN-1", "REQ-9", "ACC-1", "CREDIT", "9999", 1));
        processor.drain();

        assertThat(tampered.outcome()).isEqualTo(DeliveryOutcome.CONFLICT);
        assertThat(balance("ACC-1")).isEqualByComparingTo("100");
        assertThat(records.findById("TXN-1").orElseThrow().getAmount()).isEqualByComparingTo("100");
    }

    @Test
    void lateDuplicateAfterProcessingHasNoEffect() {
        ingestion.ingest(Txn.of("TXN-1", "REQ-1", "ACC-1", "CREDIT", "100", 1));
        processor.drain();
        assertThat(statusOf("TXN-1")).isEqualTo(PROCESSED);

        IngestionResult late = ingestion.ingest(Txn.of("TXN-1", "REQ-1", "ACC-1", "CREDIT", "100", 1));
        processor.drain();

        assertThat(late.outcome()).isEqualTo(DeliveryOutcome.DUPLICATE);
        assertThat(late.transactionStatus()).isEqualTo(PROCESSED);
        assertThat(ledger.count()).isEqualTo(1);
        assertThat(balance("ACC-1")).isEqualByComparingTo("100");
    }

    @Test
    void replayingTheWholeBatchChangesNothing() {
        faults.failNext("TXN-2005", 1);
        ingestAll(Txn.briefDataset());
        processor.drain();
        long ledgerBefore = ledger.count();

        List<IngestionResult> replay = ingestAll(Txn.briefDataset());
        processor.drain();

        assertThat(replay).extracting(IngestionResult::outcome).containsOnly(DeliveryOutcome.DUPLICATE);
        assertThat(ledger.count()).isEqualTo(ledgerBefore);
        assertThat(balance("ACC-201")).isEqualByComparingTo("85");
        assertThat(balance("ACC-203")).isEqualByComparingTo("500");
    }

    @Test
    void concurrentDeliveriesOfTheSameTransactionAreAppliedOnce() throws Exception {
        int threads = 10;
        RawTransaction txn = Txn.of("TXN-C", "REQ-C", "ACC-C", "CREDIT", "10", 1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<IngestionResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                Callable<IngestionResult> task = () -> {
                    start.await();
                    return ingestion.ingest(txn);
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            List<DeliveryOutcome> outcomes = new ArrayList<>();
            for (Future<IngestionResult> f : futures) {
                outcomes.add(f.get().outcome());
            }
            processor.drain();

            assertThat(outcomes).filteredOn(o -> o == DeliveryOutcome.ACCEPTED).hasSize(1);
            assertThat(outcomes).filteredOn(o -> o == DeliveryOutcome.DUPLICATE).hasSize(threads - 1);
            assertThat(records.count()).isEqualTo(1);
            assertThat(deliveries.count()).isEqualTo(threads);
            assertThat(ledger.count()).isEqualTo(1);
            assertThat(balance("ACC-C")).isEqualByComparingTo("10");
        } finally {
            pool.shutdownNow();
        }
    }
}
