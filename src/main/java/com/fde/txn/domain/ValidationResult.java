package com.fde.txn.domain;

import java.util.List;

/**
 * Result of validating a {@link RawTransaction}.
 * For invalid input, the identifiable parts (transactionId, accountId, sequenceNumber) are kept when they are
 * themselves well-formed, so a rejected record can still be stored and can consume its sequence slot.
 */
public record ValidationResult(
        boolean valid,
        TransactionRequest request,
        String transactionId,
        String accountId,
        Long sequenceNumber,
        List<String> errors) {

    public static ValidationResult valid(TransactionRequest request) {
        return new ValidationResult(true, request, request.transactionId(), request.accountId(),
                request.sequenceNumber(), List.of());
    }

    public static ValidationResult invalid(String transactionId, String accountId, Long sequenceNumber,
                                           List<String> errors) {
        return new ValidationResult(false, null, transactionId, accountId, sequenceNumber, List.copyOf(errors));
    }

    public String errorSummary() {
        return String.join("; ", errors);
    }
}
