package com.fde.txn.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionValidatorTest {

    private final TransactionValidator validator = new TransactionValidator();

    private static RawTransaction raw(String txn, String req, String acc, String type, String amount, String ccy,
                                      String seq) {
        return new RawTransaction(txn, req, acc, type, amount, ccy, seq, null, null);
    }

    @Test
    void acceptsValidTransactionAndNormalisesAmount() {
        ValidationResult r = validator.validate(raw("TXN-1", "REQ-1", "ACC-1", "credit", "40.00", "USD", "1"));

        assertThat(r.valid()).isTrue();
        assertThat(r.request().type()).isEqualTo(TransactionType.CREDIT);
        assertThat(r.request().amount()).isEqualByComparingTo("40");
        assertThat(r.request().sequenceNumber()).isEqualTo(1);
    }

    @ParameterizedTest(name = "{7}")
    @CsvSource(nullValues = "null", value = {
            "null,  REQ-1, ACC-1, CREDIT,   10,     USD, 1,  transactionId is required",
            "TXN-1, null,  ACC-1, CREDIT,   10,     USD, 1,  requestId is required",
            "TXN-1, REQ-1, null,  CREDIT,   10,     USD, 1,  accountId is required",
            "TXN-1, REQ-1, ACC-1, TRANSFER, 10,     USD, 1,  'transactionType must be one of CREDIT, DEBIT, REVERSAL'",
            "TXN-1, REQ-1, ACC-1, DEBIT,    -10,    USD, 1,  amount must be greater than zero",
            "TXN-1, REQ-1, ACC-1, DEBIT,    0,      USD, 1,  amount must be greater than zero",
            "TXN-1, REQ-1, ACC-1, DEBIT,    abc,    USD, 1,  amount must be a decimal number",
            "TXN-1, REQ-1, ACC-1, DEBIT,    1.12345,USD, 1,  amount supports at most 4 decimal places",
            "TXN-1, REQ-1, ACC-1, DEBIT,    10,     usd, 1,  currency must be a 3-letter ISO-4217 code",
            "TXN-1, REQ-1, ACC-1, DEBIT,    10,     USD, 0,  sequenceNumber must be >= 1",
            "TXN-1, REQ-1, ACC-1, DEBIT,    10,     USD, x,  sequenceNumber must be a whole number",
            "TXN 1, REQ-1, ACC-1, DEBIT,    10,     USD, 1,  transactionId must be 1-64 characters of [A-Za-z0-9._:-]"
    })
    void rejectsInvalidInput(String txn, String req, String acc, String type, String amount, String ccy,
                             String seq, String expectedError) {
        ValidationResult r = validator.validate(raw(txn, req, acc, type, amount, ccy, seq));

        assertThat(r.valid()).isFalse();
        assertThat(r.errors()).contains(expectedError);
    }

    @Test
    void invalidRecordKeepsIdentifiablePartsSoItCanConsumeItsSequenceSlot() {
        ValidationResult r = validator.validate(raw("TXN-2004", "REQ-4", "ACC-202", "DEBIT", "-10", "USD", "4"));

        assertThat(r.valid()).isFalse();
        assertThat(r.transactionId()).isEqualTo("TXN-2004");
        assertThat(r.accountId()).isEqualTo("ACC-202");
        assertThat(r.sequenceNumber()).isEqualTo(4L);
    }

    @Test
    void reversalRequiresAnOriginalTransaction() {
        ValidationResult missing = validator.validate(raw("TXN-9", "REQ-9", "ACC-1", "REVERSAL", "10", "USD", "2"));
        ValidationResult self = validator.validate(new RawTransaction("TXN-9", "REQ-9", "ACC-1", "REVERSAL", "10",
                "USD", "2", null, "TXN-9"));

        assertThat(missing.errors()).contains("originalTransactionId is required");
        assertThat(self.errors()).contains("originalTransactionId must reference a different transaction");
    }

    @Test
    void errorMessagesNeverEchoInputValues() {
        ValidationResult r = validator.validate(raw("TXN-1", "REQ-1", "ACC-1", "DEBIT", "<script>", "USD", "1"));

        assertThat(r.errorSummary()).doesNotContain("<script>");
    }
}
