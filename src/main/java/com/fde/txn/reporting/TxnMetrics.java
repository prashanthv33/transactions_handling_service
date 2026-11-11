package com.fde.txn.reporting;

import com.fde.txn.domain.DeliveryOutcome;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Micrometer counters, exposed through /actuator/metrics:
 * txn.deliveries{outcome=ACCEPTED|DUPLICATE|CONFLICT|REJECTED},
 * txn.processing{result=PROCESSED|FAILED|RETRY_SCHEDULED}, txn.sequence.gap.alerts.
 */
@Component
public class TxnMetrics {

    private final MeterRegistry registry;

    public TxnMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void delivery(DeliveryOutcome outcome) {
        registry.counter("txn.deliveries", "outcome", outcome.name()).increment();
    }

    public void processing(String result) {
        registry.counter("txn.processing", "result", result).increment();
    }

    public void gapAlert() {
        registry.counter("txn.sequence.gap.alerts").increment();
    }
}
