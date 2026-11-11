package com.fde.txn.processing;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Runs before any processing: records interrupted mid-apply by a crash are re-queued for retry. */
@Component
@Order(0)
public class StartupRecovery implements ApplicationRunner {

    private final TransactionProcessor processor;

    public StartupRecovery(TransactionProcessor processor) {
        this.processor = processor;
    }

    @Override
    public void run(ApplicationArguments args) {
        processor.recoverInterrupted();
    }
}
