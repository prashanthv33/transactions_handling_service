package com.fde.txn.persistence;

import com.fde.txn.domain.TransactionStatus;
import com.fde.txn.domain.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * State of one business transaction. The primary key is the business transactionId, so the database itself
 * guarantees one row per transaction no matter how many times it is delivered.
 */
@Entity
@Table(name = "transaction_record", indexes = {
        @Index(name = "ix_txn_status", columnList = "status"),
        @Index(name = "ix_txn_account_seq", columnList = "account_id,sequence_number")
})
public class TransactionRecord {

    @Id
    @Column(name = "transaction_id", length = 64)
    private String transactionId;

    /** requestId of the first (accepted) delivery. Later deliveries are in the delivery log. */
    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "account_id", length = 64)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", length = 16)
    private TransactionType type;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    @Column(name = "sequence_number")
    private Long sequenceNumber;

    @Column(name = "original_transaction_id", length = 64)
    private String originalTransactionId;

    @Column(name = "event_time")
    private Instant eventTime;

    @Column(name = "payload_hash", length = 64, nullable = false)
    private String payloadHash;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private TransactionStatus status;

    private int attempts;

    /** Set when an operator replays a FAILED record; allows applying it behind the account's sequence cursor. */
    @Column(name = "manual_replay")
    private boolean manualReplay;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 512)
    private String lastError;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Version
    private Long version;

    protected TransactionRecord() {
    }

    public TransactionRecord(String transactionId, String payloadHash, TransactionStatus status, Instant receivedAt) {
        this.transactionId = transactionId;
        this.payloadHash = payloadHash;
        this.status = status;
        this.receivedAt = receivedAt;
    }

    public String getTransactionId() { return transactionId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public TransactionType getType() { return type; }
    public void setType(TransactionType type) { this.type = type; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Long getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(Long sequenceNumber) { this.sequenceNumber = sequenceNumber; }
    public String getOriginalTransactionId() { return originalTransactionId; }
    public void setOriginalTransactionId(String originalTransactionId) { this.originalTransactionId = originalTransactionId; }
    public Instant getEventTime() { return eventTime; }
    public void setEventTime(Instant eventTime) { this.eventTime = eventTime; }
    public String getPayloadHash() { return payloadHash; }
    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public boolean isManualReplay() { return manualReplay; }
    public void setManualReplay(boolean manualReplay) { this.manualReplay = manualReplay; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
