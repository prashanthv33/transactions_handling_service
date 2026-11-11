package com.fde.txn;

import com.fde.txn.support.IntegrationTestBase;
import com.fde.txn.support.Txn;
import org.junit.jupiter.api.Test;

import static com.fde.txn.domain.TransactionStatus.FAILED;
import static com.fde.txn.domain.TransactionStatus.PROCESSED;
import static org.assertj.core.api.Assertions.assertThat;

class ReversalTest extends IntegrationTestBase {

    @Test
    void reversalUndoesTheOriginalExactlyOnce() {
        ingestion.ingest(Txn.of("TXN-1", "REQ-1", "ACC-1", "CREDIT", "100", 1));
        ingestion.ingest(Txn.of("TXN-2", "REQ-2", "ACC-1", "DEBIT", "40", 2));
        ingestion.ingest(Txn.reversal("TXN-3", "REQ-3", "ACC-1", "40", 3, "TXN-2"));
        ingestion.ingest(Txn.reversal("TXN-4", "REQ-4", "ACC-1", "40", 4, "TXN-2"));
        processor.drain();

        assertThat(statusOf("TXN-3")).isEqualTo(PROCESSED);
        assertThat(statusOf("TXN-4")).isEqualTo(FAILED);
        assertThat(records.findById("TXN-4").orElseThrow().getLastError()).startsWith("ALREADY_REVERSED");
        assertThat(balance("ACC-1")).isEqualByComparingTo("100");
    }

    @Test
    void partialReversalIsRejected() {
        ingestion.ingest(Txn.of("TXN-1", "REQ-1", "ACC-1", "CREDIT", "100", 1));
        ingestion.ingest(Txn.reversal("TXN-2", "REQ-2", "ACC-1", "10", 2, "TXN-1"));
        processor.drain();

        assertThat(statusOf("TXN-2")).isEqualTo(FAILED);
        assertThat(records.findById("TXN-2").orElseThrow().getLastError()).startsWith("REVERSAL_AMOUNT_MISMATCH");
        assertThat(balance("ACC-1")).isEqualByComparingTo("100");
    }

    @Test
    void reversalOfUnknownTransactionFails() {
        ingestion.ingest(Txn.of("TXN-1", "REQ-1", "ACC-1", "CREDIT", "100", 1));
        ingestion.ingest(Txn.reversal("TXN-2", "REQ-2", "ACC-1", "10", 2, "TXN-404"));
        processor.drain();

        assertThat(records.findById("TXN-2").orElseThrow().getLastError()).startsWith("REVERSAL_ORIGINAL_NOT_FOUND");
    }
}
