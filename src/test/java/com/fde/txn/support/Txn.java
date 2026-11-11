package com.fde.txn.support;

import com.fde.txn.domain.RawTransaction;

import java.util.List;

/** Test data builder for raw transaction input. */
public final class Txn {

    private Txn() {
    }

    public static RawTransaction of(String txnId, String reqId, String account, String type, String amount,
                                    long seq) {
        return new RawTransaction(txnId, reqId, account, type, amount, "USD", Long.toString(seq), null, null);
    }

    public static RawTransaction reversal(String txnId, String reqId, String account, String amount, long seq,
                                          String original) {
        return new RawTransaction(txnId, reqId, account, "REVERSAL", amount, "USD", Long.toString(seq), null,
                original);
    }

    /** The suggested L2 test data from the brief, section 9.8, in its original arrival order. */
    public static List<RawTransaction> briefDataset() {
        return List.of(
                of("TXN-2002", "REQ-2", "ACC-201", "DEBIT", "40", 2),
                of("TXN-2001", "REQ-1", "ACC-201", "CREDIT", "100", 1),
                of("TXN-2002", "REQ-2-DUP", "ACC-201", "DEBIT", "40", 2),
                of("TXN-2003", "REQ-3", "ACC-201", "CREDIT", "25", 3),
                of("TXN-2004", "REQ-4", "ACC-202", "DEBIT", "-10", 4),
                of("TXN-2005", "REQ-5", "ACC-203", "CREDIT", "500", 5));
    }
}
