package com.fde.txn.domain;

/** Outcome of a single delivery (request) of a transaction, recorded in the delivery log. */
public enum DeliveryOutcome {
    /** First delivery of this transactionId; stored for processing. */
    ACCEPTED,
    /** Same transactionId and identical business payload as an earlier delivery; ignored. */
    DUPLICATE,
    /** Same transactionId but a different business payload; flagged, never applied. */
    CONFLICT,
    /** Failed validation. */
    REJECTED
}
