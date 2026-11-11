package com.fde.txn.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadHasherTest {

    private final TransactionValidator validator = new TransactionValidator();
    private final PayloadHasher hasher = new PayloadHasher();

    private String hashOf(String req, String amount, String eventTime) {
        TransactionRequest r = validator.validate(new RawTransaction("TXN-1", req, "ACC-1", "DEBIT", amount, "USD",
                "2", eventTime, null)).request();
        return hasher.hash(r);
    }

    @Test
    void ignoresRequestIdAndEventTimeSoRetriesAreRecognisedAsDuplicates() {
        assertThat(hashOf("REQ-2", "40", "2026-01-01T00:00:00Z"))
                .isEqualTo(hashOf("REQ-2-DUP", "40", "2026-01-01T00:05:00Z"));
    }

    @Test
    void ignoresAmountScale() {
        assertThat(hashOf("REQ-2", "40", null)).isEqualTo(hashOf("REQ-2", "40.00", null));
    }

    @Test
    void detectsBusinessPayloadChanges() {
        assertThat(hashOf("REQ-2", "40", null)).isNotEqualTo(hashOf("REQ-2", "41", null));
    }
}
