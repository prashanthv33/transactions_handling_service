package com.fde.txn.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;

/** Account balance plus the per-account sequence cursor used for ordering. Optimistically locked. */
@Entity
@Table(name = "account")
public class Account {

    @Id
    @Column(name = "account_id", length = 64)
    private String accountId;

    @Column(length = 3, nullable = false)
    private String currency;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal balance;

    /** Highest sequence number applied to this account. */
    @Column(name = "last_applied_seq", nullable = false)
    private long lastAppliedSeq;

    @Version
    private Long version;

    protected Account() {
    }

    public Account(String accountId, String currency) {
        this.accountId = accountId;
        this.currency = currency;
        this.balance = BigDecimal.ZERO;
        this.lastAppliedSeq = 0;
    }

    public String getAccountId() { return accountId; }
    public String getCurrency() { return currency; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public long getLastAppliedSeq() { return lastAppliedSeq; }
    public void setLastAppliedSeq(long lastAppliedSeq) { this.lastAppliedSeq = lastAppliedSeq; }
}
