package com.fde.txn;

import com.fde.txn.domain.RawTransaction;
import com.fde.txn.reporting.ReportService;
import com.fde.txn.reporting.Views.GapView;
import com.fde.txn.support.IntegrationTestBase;
import com.fde.txn.support.Txn;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static com.fde.txn.domain.TransactionStatus.FAILED;
import static com.fde.txn.domain.TransactionStatus.PENDING;
import static com.fde.txn.domain.TransactionStatus.PROCESSED;
import static org.assertj.core.api.Assertions.assertThat;

class OutOfOrderTest extends IntegrationTestBase {

    @Autowired
    ReportService reports;

    /** Order matters here: applied in arrival order, the debits would overdraw the account. */
    private static List<RawTransaction> orderSensitiveDataset() {
        List<RawTransaction> list = new ArrayList<>(Txn.briefDataset());
        list.add(Txn.of("TXN-4001", "REQ-4001", "ACC-401", "CREDIT", "30", 1));
        list.add(Txn.of("TXN-4002", "REQ-4002", "ACC-401", "DEBIT", "30", 2));
        list.add(Txn.of("TXN-4003", "REQ-4003", "ACC-401", "CREDIT", "70", 3));
        list.add(Txn.of("TXN-4004", "REQ-4004", "ACC-401", "DEBIT", "60", 4));
        list.add(Txn.of("TXN-4002", "REQ-4002-DUP", "ACC-401", "DEBIT", "30", 2));
        return list;
    }

    @Test
    void anyArrivalOrderProducesTheSameFinalState() {
        for (long seed = 1; seed <= 25; seed++) {
            cleanState();
            faults.failNext("TXN-2005", 1);
            List<RawTransaction> shuffled = new ArrayList<>(orderSensitiveDataset());
            Collections.shuffle(shuffled, new Random(seed));

            ingestAll(shuffled);
            processor.drain();

            assertThat(balance("ACC-201")).as("seed %d", seed).isEqualByComparingTo("85");
            assertThat(balance("ACC-203")).as("seed %d", seed).isEqualByComparingTo("500");
            assertThat(balance("ACC-401")).as("seed %d", seed).isEqualByComparingTo("10");
            assertThat(ledger.count()).as("seed %d", seed).isEqualTo(8);
            assertThat(records.countByStatus(PROCESSED)).as("seed %d", seed).isEqualTo(8);
        }
    }

    @Test
    void laterSequenceWaitsAsPendingUntilTheGapIsFilled() {
        ingestion.ingest(Txn.of("TXN-1", "REQ-1", "ACC-1", "CREDIT", "100", 1));
        processor.drain();

        ingestion.ingest(Txn.of("TXN-3", "REQ-3", "ACC-1", "DEBIT", "30", 3));
        processor.processOnce(false);
        assertThat(statusOf("TXN-3")).isEqualTo(PENDING);

        List<GapView> gaps = reports.summary().sequenceGaps();
        assertThat(gaps).singleElement().satisfies(g -> {
            assertThat(g.accountId()).isEqualTo("ACC-1");
            assertThat(g.missingSequences()).containsExactly(2L);
            assertThat(g.waitingTransactions()).containsExactly("TXN-3");
        });

        ingestion.ingest(Txn.of("TXN-2", "REQ-2", "ACC-1", "DEBIT", "50", 2));
        processor.processOnce(false);

        assertThat(statusOf("TXN-2")).isEqualTo(PROCESSED);
        assertThat(statusOf("TXN-3")).isEqualTo(PROCESSED);
        assertThat(balance("ACC-1")).isEqualByComparingTo("20");
        assertThat(reports.summary().sequenceGaps()).isEmpty();
    }

    @Test
    void newAccountIsBufferedForTheReorderWindowInStreamingMode() {
        ingestion.ingest(Txn.of("TXN-2", "REQ-2", "ACC-N", "DEBIT", "40", 2));
        processor.processOnce(false); // window (5s) still open
        assertThat(statusOf("TXN-2")).isEqualTo(PENDING);
        assertThat(accounts.findById("ACC-N")).isEmpty();

        ingestion.ingest(Txn.of("TXN-1", "REQ-1", "ACC-N", "CREDIT", "100", 1));
        processor.processOnce(true); // window closed: baseline at the lowest sequence seen

        assertThat(statusOf("TXN-1")).isEqualTo(PROCESSED);
        assertThat(statusOf("TXN-2")).isEqualTo(PROCESSED);
        assertThat(balance("ACC-N")).isEqualByComparingTo("60");
    }

    @Test
    void rejectedRecordConsumesItsSequenceSlotAndDoesNotBlockTheAccount() {
        ingestion.ingest(Txn.of("TXN-1", "REQ-1", "ACC-1", "CREDIT", "100", 1));
        ingestion.ingest(Txn.of("TXN-2", "REQ-2", "ACC-1", "DEBIT", "-5", 2)); // invalid
        ingestion.ingest(Txn.of("TXN-3", "REQ-3", "ACC-1", "DEBIT", "30", 3));
        processor.drain();

        assertThat(statusOf("TXN-3")).isEqualTo(PROCESSED);
        assertThat(balance("ACC-1")).isEqualByComparingTo("70");
    }

    @Test
    void validRecordTakesPrecedenceOverARejectedRecordWithTheSameSequence() {
        // Regression: a rejected record must not "steal" the slot of a valid transaction with the same sequence.
        ingestion.ingest(Txn.of("TXN-BAD", "REQ-BAD", "ACC-1", "CREDIT", "abc", 1));
        ingestion.ingest(Txn.of("TXN-1", "REQ-1", "ACC-1", "CREDIT", "100", 1));
        processor.drain();

        assertThat(statusOf("TXN-1")).isEqualTo(PROCESSED);
        assertThat(balance("ACC-1")).isEqualByComparingTo("100");
    }

    @Test
    void differentTransactionReusingAnAppliedSequenceIsFailed() {
        ingestion.ingest(Txn.of("TXN-1", "REQ-1", "ACC-1", "CREDIT", "100", 1));
        processor.drain();
        ingestion.ingest(Txn.of("TXN-1B", "REQ-1B", "ACC-1", "CREDIT", "100", 1));
        processor.drain();

        assertThat(statusOf("TXN-1B")).isEqualTo(FAILED);
        assertThat(records.findById("TXN-1B").orElseThrow().getLastError()).startsWith("SEQUENCE_ALREADY_CONSUMED");
        assertThat(balance("ACC-1")).isEqualByComparingTo("100");
    }
}
