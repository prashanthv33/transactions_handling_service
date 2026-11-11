package com.fde.txn.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable ledger line: the only "side effect" of processing.
 * UNIQUE(transaction_id) and UNIQUE(reversal_of) are the database-level backstop against double application.
 */
@Entity
@Table(name = "ledger_entry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ledger_transaction", columnNames = "transaction_id"),
        @UniqueConstraint(name = "uk_ledger_reversal_of", columnNames = "reversal_of")
})
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", length = 64, nullable = false)
    private String transactionId;

    @Column(name = "account_id", length = 64, nullable = false)
    private String accountId;

    /** Signed amount: positive for credits, negative for debits. */
    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "balance_after", precision = 19, scale = 4, nullable = false)
    private BigDecimal balanceAfter;

    /** For REVERSAL entries: the transactionId being reversed. */
    @Column(name = "reversal_of", length = 64)
    private String reversalOf;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(String transactionId, String accountId, BigDecimal amount, BigDecimal balanceAfter,
                       String reversalOf, Instant createdAt) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.reversalOf = reversalOf;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getTransactionId() { return transactionId; }
    public String getAccountId() { return accountId; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getReversalOf() { return reversalOf; }
    public Instant getCreatedAt() { return createdAt; }
}
