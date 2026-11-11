package com.fde.txn.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    boolean existsByTransactionId(String transactionId);

    boolean existsByReversalOf(String transactionId);

    Optional<LedgerEntry> findByTransactionId(String transactionId);

    long countByTransactionId(String transactionId);

    List<LedgerEntry> findByAccountIdOrderByIdAsc(String accountId);
}
