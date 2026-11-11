package com.fde.txn.processing;

/**
 * Hook for simulating transient infrastructure failures. It is invoked inside the apply transaction <b>after</b>
 * the ledger and balance writes, which is the worst possible moment to fail, to prove that rollback plus retry
 * never produces a duplicate effect.
 */
public interface FaultInjector {

    void beforeCommit(String transactionId);
}
