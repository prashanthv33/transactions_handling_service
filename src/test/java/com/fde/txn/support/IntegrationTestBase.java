package com.fde.txn.support;

import com.fde.txn.domain.RawTransaction;
import com.fde.txn.domain.TransactionStatus;
import com.fde.txn.ingestion.IngestionResult;
import com.fde.txn.ingestion.IngestionService;
import com.fde.txn.persistence.AccountRepository;
import com.fde.txn.persistence.DeliveryLogRepository;
import com.fde.txn.persistence.LedgerEntryRepository;
import com.fde.txn.persistence.TransactionRecordRepository;
import com.fde.txn.processing.ConfigurableFaultInjector;
import com.fde.txn.processing.TransactionProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

/**
 * Full Spring context with an isolated in-memory H2 database. The scheduled worker is disabled so each test
 * drives the processor deterministically; retry backoff is zero so tests run fast.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:txn-test;DB_CLOSE_DELAY=-1",
        "processing.worker.enabled=false",
        "processing.base-backoff=0ms",
        "processing.reorder-window=5s",
        "logging.level.com.fde.txn=WARN"
})
public abstract class IntegrationTestBase {

    @Autowired protected IngestionService ingestion;
    @Autowired protected TransactionProcessor processor;
    @Autowired protected ConfigurableFaultInjector faults;
    @Autowired protected TransactionRecordRepository records;
    @Autowired protected AccountRepository accounts;
    @Autowired protected LedgerEntryRepository ledger;
    @Autowired protected DeliveryLogRepository deliveries;

    @BeforeEach
    protected void cleanState() {
        ledger.deleteAllInBatch();
        accounts.deleteAllInBatch();
        records.deleteAllInBatch();
        deliveries.deleteAllInBatch();
        faults.reset();
    }

    protected List<IngestionResult> ingestAll(List<RawTransaction> raws) {
        return raws.stream().map(ingestion::ingest).toList();
    }

    protected TransactionStatus statusOf(String txnId) {
        return records.findById(txnId).orElseThrow().getStatus();
    }

    protected BigDecimal balance(String accountId) {
        return accounts.findById(accountId).orElseThrow().getBalance();
    }
}
