package com.fde.txn.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fde.txn.domain.DeliveryOutcome;
import com.fde.txn.domain.RawTransaction;
import com.fde.txn.processing.TransactionProcessor;
import com.fde.txn.reporting.ProcessingSummary;
import com.fde.txn.reporting.ReportService;
import com.fde.txn.reporting.Views.AccountView;
import com.fde.txn.reporting.Views.TransactionView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST adapter. POST only ingests (fast, idempotent); balances are applied asynchronously by the background
 * worker, so callers receive 202 Accepted and can poll GET /transactions/{id}.
 */
@RestController
public class TransactionController {

    private final IngestionService ingestion;
    private final ReportService reports;
    private final TransactionProcessor processor;

    public TransactionController(IngestionService ingestion, ReportService reports, TransactionProcessor processor) {
        this.ingestion = ingestion;
        this.reports = reports;
        this.processor = processor;
    }

    /** Accepts a single transaction object or an array of them. */
    @PostMapping("/transactions")
    public ResponseEntity<?> submit(@RequestBody JsonNode body) {
        List<RawTransaction> raws = RawTransactionMapper.fromJson(body);
        if (body.isArray()) {
            List<IngestionResult> results = raws.stream().map(ingestion::ingest).toList();
            return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(results);
        }
        IngestionResult result = ingestion.ingest(raws.get(0));
        return ResponseEntity.status(statusFor(result.outcome())).body(result);
    }

    @GetMapping("/transactions/{transactionId}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String transactionId) {
        return reports.transaction(transactionId)
                .<ResponseEntity<Map<String, Object>>>map(t -> ResponseEntity.ok(Map.of(
                        "transaction", t, "deliveries", reports.deliveriesFor(transactionId))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/transactions")
    public List<TransactionView> list() {
        return reports.transactions();
    }

    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<AccountView> account(@PathVariable String accountId) {
        return reports.account(accountId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/report")
    public ProcessingSummary report() {
        return reports.summary();
    }

    /** Operator action: re-queue a FAILED transaction (e.g. after a downstream outage is fixed). */
    @PostMapping("/admin/transactions/{transactionId}/replay")
    public ResponseEntity<Map<String, String>> replay(@PathVariable String transactionId) {
        if (processor.replay(transactionId)) {
            return ResponseEntity.accepted().body(Map.of("transactionId", transactionId, "status", "RETRY_PENDING"));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("transactionId", transactionId,
                "error", "Only FAILED transactions that passed validation can be replayed"));
    }

    private static HttpStatus statusFor(DeliveryOutcome outcome) {
        return switch (outcome) {
            case ACCEPTED -> HttpStatus.ACCEPTED;
            case DUPLICATE -> HttpStatus.OK;
            case CONFLICT -> HttpStatus.CONFLICT;
            case REJECTED -> HttpStatus.BAD_REQUEST;
        };
    }
}
