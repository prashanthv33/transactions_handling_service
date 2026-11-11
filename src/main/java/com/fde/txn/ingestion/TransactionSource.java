package com.fde.txn.ingestion;

import com.fde.txn.domain.RawTransaction;

import java.util.List;

/**
 * A replaceable input source (L2-FR-01). Implementations: {@link JsonFileTransactionSource} for batch files and
 * {@link TransactionController} for the REST API. A Kafka or SQS consumer would be another adapter feeding
 * {@link IngestionService} the same way.
 */
public interface TransactionSource {

    List<RawTransaction> read();
}
