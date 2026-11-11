package com.fde.txn.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Server mode: polls for work in the background, as a queue consumer or job scheduler would. */
@Component
@ConditionalOnProperty(name = "processing.worker.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledWorker {

    private static final Logger log = LoggerFactory.getLogger(ScheduledWorker.class);

    private final TransactionProcessor processor;

    public ScheduledWorker(TransactionProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${processing.worker.interval-ms:500}")
    public void tick() {
        try {
            processor.processOnce(false);
        } catch (RuntimeException e) {
            // Never let one bad pass kill the scheduler thread; the next tick retries.
            log.error("event=worker.pass_failed error={}", e.getClass().getSimpleName(), e);
        }
    }
}
