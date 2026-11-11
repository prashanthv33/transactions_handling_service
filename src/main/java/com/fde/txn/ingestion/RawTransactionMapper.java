package com.fde.txn.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fde.txn.domain.RawTransaction;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps untrusted JSON into {@link RawTransaction} field by field without type coercion failures, so a single
 * malformed record never breaks parsing of the whole batch. Accepts a few common field-name aliases.
 */
public final class RawTransactionMapper {

    private RawTransactionMapper() {
    }

    public static List<RawTransaction> fromJson(JsonNode node) {
        List<RawTransaction> result = new ArrayList<>();
        if (node == null || node.isNull()) {
            return result;
        }
        if (node.isArray()) {
            node.forEach(element -> result.add(fromObject(element)));
        } else {
            result.add(fromObject(node));
        }
        return result;
    }

    public static RawTransaction fromObject(JsonNode n) {
        return new RawTransaction(
                text(n, "transactionId"),
                text(n, "requestId", "idempotencyKey"),
                text(n, "accountId"),
                text(n, "transactionType", "type"),
                text(n, "amount"),
                text(n, "currency"),
                text(n, "sequenceNumber", "sequence"),
                text(n, "eventTime"),
                text(n, "originalTransactionId"));
    }

    private static String text(JsonNode n, String... names) {
        if (n == null || !n.isObject()) {
            return null;
        }
        for (String name : names) {
            JsonNode v = n.get(name);
            if (v != null && !v.isNull()) {
                // Non-scalar values (objects or arrays) become an unparseable marker and fail validation.
                return v.isValueNode() ? v.asText() : "<non-scalar>";
            }
        }
        return null;
    }
}
