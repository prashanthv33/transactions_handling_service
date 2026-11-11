package com.fde.txn.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fde.txn.domain.RawTransaction;
import com.fde.txn.ingestion.IngestionResult;
import com.fde.txn.ingestion.IngestionService;
import com.fde.txn.ingestion.JsonFileTransactionSource;
import com.fde.txn.processing.TransactionProcessor;
import com.fde.txn.reporting.ReportFormatter;
import com.fde.txn.reporting.ReportService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Batch mode (profile "batch"): ingest a file, drain the background processor, print the report, exit.
 * Usage: java -jar target/txn-service.jar --spring.profiles.active=batch --input=samples/input.json
 */
@Component
@Profile("batch")
@Order(10)
public class BatchRunner implements ApplicationRunner {

    private final IngestionService ingestion;
    private final TransactionProcessor processor;
    private final ReportService reports;
    private final ObjectMapper objectMapper;

    public BatchRunner(IngestionService ingestion, TransactionProcessor processor, ReportService reports,
                       ObjectMapper objectMapper) {
        this.ingestion = ingestion;
        this.processor = processor;
        this.reports = reports;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> input = args.getOptionValues("input");
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Missing --input=<path to JSON file>");
        }
        List<RawTransaction> raws = new JsonFileTransactionSource(Path.of(input.get(0)), objectMapper).read();

        List<IngestionResult> results = raws.stream().map(ingestion::ingest).toList();
        System.out.print(ReportFormatter.ingestion(results));

        processor.drain();

        System.out.print(ReportFormatter.report(reports.summary(), reports.transactions()));
    }
}
