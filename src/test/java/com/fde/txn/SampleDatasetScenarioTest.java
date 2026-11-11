package com.fde.txn;

import com.fde.txn.domain.DeliveryOutcome;
import com.fde.txn.ingestion.IngestionResult;
import com.fde.txn.support.IntegrationTestBase;
import com.fde.txn.support.Txn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.fde.txn.domain.TransactionStatus.PROCESSED;
import static com.fde.txn.domain.TransactionStatus.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;

/** End-to-end run of the brief's suggested L2 test data (section 9.8). */
class SampleDatasetScenarioTest extends IntegrationTestBase {

    @Test
    @DisplayName("Brief 9.8 dataset: every row reaches its expected outcome")
    void briefDataset() {
        faults.failNext("TXN-2005", 1); // "Retry once, then processed"

        List<IngestionResult> intake = ingestAll(Txn.briefDataset());
        processor.drain();

        // Delivery outcomes, in arrival order
        assertThat(intake).extracting(IngestionResult::outcome).containsExactly(
                DeliveryOutcome.ACCEPTED,   // TXN-2002 arrives first (out of order)
                DeliveryOutcome.ACCEPTED,   // TXN-2001
                DeliveryOutcome.DUPLICATE,  // TXN-2002 again with REQ-2-DUP
                DeliveryOutcome.ACCEPTED,   // TXN-2003
                DeliveryOutcome.REJECTED,   // TXN-2004 negative amount
                DeliveryOutcome.ACCEPTED);  // TXN-2005

        // Final transaction states
        assertThat(statusOf("TXN-2001")).isEqualTo(PROCESSED);
        assertThat(statusOf("TXN-2002")).isEqualTo(PROCESSED);
        assertThat(statusOf("TXN-2003")).isEqualTo(PROCESSED);
        assertThat(statusOf("TXN-2004")).isEqualTo(REJECTED);
        assertThat(statusOf("TXN-2005")).isEqualTo(PROCESSED);
        assertThat(records.findById("TXN-2005").orElseThrow().getAttempts()).isEqualTo(2);

        // Business outcome: the debit was applied after the credit, exactly once
        assertThat(balance("ACC-201")).isEqualByComparingTo("85");
        assertThat(balance("ACC-203")).isEqualByComparingTo("500");
        assertThat(accounts.findById("ACC-202")).isEmpty();
        assertThat(ledger.count()).isEqualTo(4);
        assertThat(ledger.countByTransactionId("TXN-2002")).isEqualTo(1);
    }
}
