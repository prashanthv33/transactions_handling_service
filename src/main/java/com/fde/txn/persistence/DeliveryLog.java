package com.fde.txn.persistence;

import com.fde.txn.domain.DeliveryOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/** Append-only audit of every delivery received, including duplicates, conflicts and rejections. */
@Entity
@Table(name = "delivery_log", indexes = @Index(name = "ix_delivery_txn", columnList = "transaction_id"))
public class DeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "transaction_id", length = 64)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private DeliveryOutcome outcome;

    @Column(length = 512)
    private String reason;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected DeliveryLog() {
    }

    public DeliveryLog(String requestId, String transactionId, DeliveryOutcome outcome, String reason,
                       Instant receivedAt) {
        this.requestId = requestId;
        this.transactionId = transactionId;
        this.outcome = outcome;
        this.reason = reason;
        this.receivedAt = receivedAt;
    }

    public Long getId() { return id; }
    public String getRequestId() { return requestId; }
    public String getTransactionId() { return transactionId; }
    public DeliveryOutcome getOutcome() { return outcome; }
    public String getReason() { return reason; }
    public Instant getReceivedAt() { return receivedAt; }
}
