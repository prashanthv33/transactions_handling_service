package com.fde.txn.domain;

public enum TransactionType {
    CREDIT,
    DEBIT,
    /** Fully reverses a previously PROCESSED transaction referenced by originalTransactionId. */
    REVERSAL
}
