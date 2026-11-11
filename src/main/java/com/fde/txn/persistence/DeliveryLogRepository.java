package com.fde.txn.persistence;

import com.fde.txn.domain.DeliveryOutcome;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryLogRepository extends JpaRepository<DeliveryLog, Long> {

    long countByOutcome(DeliveryOutcome outcome);

    List<DeliveryLog> findByOutcomeOrderByIdAsc(DeliveryOutcome outcome);

    List<DeliveryLog> findByTransactionIdOrderByIdAsc(String transactionId);
}
