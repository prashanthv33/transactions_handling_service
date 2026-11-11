package com.fde.txn.app;

import com.fde.txn.domain.PayloadHasher;
import com.fde.txn.domain.TransactionValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneOffset;

/** Wires the framework-free domain components into the Spring context. */
@Configuration
public class AppConfig {

    @Bean
    public TransactionValidator transactionValidator() {
        return new TransactionValidator();
    }

    @Bean
    public PayloadHasher payloadHasher() {
        return new PayloadHasher();
    }

    /** Millisecond precision so instants round-trip through the database exactly (no sub-micro rounding). */
    @Bean
    public Clock clock() {
        return Clock.tickMillis(ZoneOffset.UTC);
    }
}
