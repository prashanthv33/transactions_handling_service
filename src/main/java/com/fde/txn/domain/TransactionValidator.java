package com.fde.txn.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Pure input validation. Error messages never echo raw input values, so they are safe to return and to log. */
public final class TransactionValidator {

    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");
    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000000");
    private static final int MAX_SCALE = 4;

    public ValidationResult validate(RawTransaction raw) {
        List<String> errors = new ArrayList<>();

        String transactionId = id(raw.transactionId(), "transactionId", errors);
        String requestId = id(raw.requestId(), "requestId", errors);
        String accountId = id(raw.accountId(), "accountId", errors);
        TransactionType type = type(raw.transactionType(), errors);
        BigDecimal amount = amount(raw.amount(), errors);
        String currency = currency(raw.currency(), errors);
        Long sequence = sequence(raw.sequenceNumber(), errors);
        Instant eventTime = eventTime(raw.eventTime(), errors);

        String originalTransactionId = null;
        if (type == TransactionType.REVERSAL) {
            originalTransactionId = id(raw.originalTransactionId(), "originalTransactionId", errors);
            if (originalTransactionId != null && originalTransactionId.equals(transactionId)) {
                errors.add("originalTransactionId must reference a different transaction");
            }
        }

        if (!errors.isEmpty()) {
            return ValidationResult.invalid(transactionId, accountId, sequence, errors);
        }
        return ValidationResult.valid(new TransactionRequest(transactionId, requestId, accountId, type, amount,
                currency, sequence, eventTime, originalTransactionId));
    }

    private static String id(String value, String field, List<String> errors) {
        String v = trim(value);
        if (v == null) {
            errors.add(field + " is required");
            return null;
        }
        if (!ID.matcher(v).matches()) {
            errors.add(field + " must be 1-64 characters of [A-Za-z0-9._:-]");
            return null;
        }
        return v;
    }

    private static TransactionType type(String value, List<String> errors) {
        String v = trim(value);
        if (v == null) {
            errors.add("transactionType is required");
            return null;
        }
        try {
            return TransactionType.valueOf(v.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            errors.add("transactionType must be one of CREDIT, DEBIT, REVERSAL");
            return null;
        }
    }

    private static BigDecimal amount(String value, List<String> errors) {
        String v = trim(value);
        if (v == null) {
            errors.add("amount is required");
            return null;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(v);
        } catch (NumberFormatException e) {
            errors.add("amount must be a decimal number");
            return null;
        }
        if (amount.signum() <= 0) {
            errors.add("amount must be greater than zero");
            return null;
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            errors.add("amount exceeds the maximum allowed value");
            return null;
        }
        BigDecimal normalized = amount.stripTrailingZeros();
        if (normalized.scale() > MAX_SCALE) {
            errors.add("amount supports at most " + MAX_SCALE + " decimal places");
            return null;
        }
        return normalized.scale() < 0 ? normalized.setScale(0) : normalized;
    }

    private static String currency(String value, List<String> errors) {
        String v = trim(value);
        if (v == null) {
            errors.add("currency is required");
            return null;
        }
        if (!CURRENCY.matcher(v).matches()) {
            errors.add("currency must be a 3-letter ISO-4217 code");
            return null;
        }
        return v;
    }

    private static Long sequence(String value, List<String> errors) {
        String v = trim(value);
        if (v == null) {
            errors.add("sequenceNumber is required");
            return null;
        }
        try {
            long seq = Long.parseLong(v);
            if (seq < 1) {
                errors.add("sequenceNumber must be >= 1");
                return null;
            }
            return seq;
        } catch (NumberFormatException e) {
            errors.add("sequenceNumber must be a whole number");
            return null;
        }
    }

    private static Instant eventTime(String value, List<String> errors) {
        String v = trim(value);
        if (v == null) {
            return null; // optional
        }
        try {
            return Instant.parse(v);
        } catch (DateTimeParseException e) {
            errors.add("eventTime must be an ISO-8601 instant, e.g. 2026-01-01T10:00:00Z");
            return null;
        }
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
