package com.fde.txn.persistence;

import com.fde.txn.domain.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TransactionRecordRepository extends JpaRepository<TransactionRecord, String> {

    List<TransactionRecord> findByStatusIn(Collection<TransactionStatus> statuses);

    List<TransactionRecord> findByStatus(TransactionStatus status);

    /** Records that occupy a sequence slot without being applied (REJECTED / FAILED). */
    List<TransactionRecord> findByAccountIdAndStatusInAndSequenceNumberNotNull(
            String accountId, Collection<TransactionStatus> statuses);

    long countByStatus(TransactionStatus status);

    @Query("select min(t.nextAttemptAt) from TransactionRecord t where t.status = :status")
    Optional<Instant> findEarliestNextAttempt(TransactionStatus status);
}
