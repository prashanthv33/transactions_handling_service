package com.fde.txn.processing;

import com.fde.txn.domain.TransientProcessingException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Fails a configured transaction N times with a transient error (processing.fault.transient-failures). */
@Component
public class ConfigurableFaultInjector implements FaultInjector {

    private final Map<String, AtomicInteger> remainingFailures = new ConcurrentHashMap<>();

    public ConfigurableFaultInjector(ProcessingProperties properties) {
        properties.fault().transientFailures().forEach(this::failNext);
    }

    @Override
    public void beforeCommit(String transactionId) {
        AtomicInteger remaining = remainingFailures.get(transactionId);
        if (remaining != null && remaining.getAndUpdate(n -> Math.max(n - 1, 0)) > 0) {
            throw new TransientProcessingException("Simulated transient downstream failure");
        }
    }

    public void failNext(String transactionId, int times) {
        remainingFailures.put(transactionId, new AtomicInteger(times));
    }

    public void reset() {
        remainingFailures.clear();
    }
}
