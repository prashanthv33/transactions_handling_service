package com.fde.txn.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Fingerprints the business content of a transaction so a true duplicate (same payload) can be told apart from a
 * conflicting reuse of the same transactionId (different payload).
 * requestId and eventTime are deliberately excluded: retries legitimately carry new request ids and timestamps.
 */
public final class PayloadHasher {

    public String hash(TransactionRequest r) {
        return sha256(String.join("|",
                "v1",
                r.transactionId(),
                r.accountId(),
                r.type().name(),
                r.amount().stripTrailingZeros().toPlainString(),
                r.currency(),
                Long.toString(r.sequenceNumber()),
                Objects.toString(r.originalTransactionId(), "")));
    }

    /** Hash for input that failed validation, so identical invalid re-deliveries are still detected as duplicates. */
    public String hashRaw(RawTransaction r) {
        return sha256(String.join("|",
                "raw",
                norm(r.transactionId()),
                norm(r.accountId()),
                norm(r.transactionType()),
                norm(r.amount()),
                norm(r.currency()),
                norm(r.sequenceNumber()),
                norm(r.originalTransactionId())));
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
