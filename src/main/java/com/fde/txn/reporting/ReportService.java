package com.fde.txn.reporting;

import com.fde.txn.domain.DeliveryOutcome;
import com.fde.txn.domain.TransactionStatus;
import com.fde.txn.persistence.Account;
import com.fde.txn.persistence.AccountRepository;
import com.fde.txn.persistence.DeliveryLogRepository;
import com.fde.txn.persistence.LedgerEntryRepository;
import com.fde.txn.persistence.TransactionRecord;
import com.fde.txn.persistence.TransactionRecordRepository;
import com.fde.txn.reporting.Views.AccountView;
import com.fde.txn.reporting.Views.DeliveryView;
import com.fde.txn.reporting.Views.FailureView;
import com.fde.txn.reporting.Views.GapView;
import com.fde.txn.reporting.Views.LedgerView;
import com.fde.txn.reporting.Views.TransactionView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private final TransactionRecordRepository records;
    private final DeliveryLogRepository deliveries;
    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final Clock clock;

    public ReportService(TransactionRecordRepository records, DeliveryLogRepository deliveries,
                         AccountRepository accounts, LedgerEntryRepository ledger, Clock clock) {
        this.records = records;
        this.deliveries = deliveries;
        this.accounts = accounts;
        this.ledger = ledger;
        this.clock = clock;
    }

    public ProcessingSummary summary() {
        List<TransactionRecord> all = records.findAll();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (TransactionStatus s : TransactionStatus.values()) {
            byStatus.put(s.name(), all.stream().filter(r -> r.getStatus() == s).count());
        }
        Map<String, Long> byOutcome = new LinkedHashMap<>();
        long totalDeliveries = 0;
        for (DeliveryOutcome o : DeliveryOutcome.values()) {
            long c = deliveries.countByOutcome(o);
            byOutcome.put(o.name(), c);
            totalDeliveries += c;
        }

        long retried = all.stream().filter(r -> r.getAttempts() > 1).count();
        long retryAttempts = all.stream().mapToLong(r -> Math.max(r.getAttempts() - 1, 0)).sum();

        List<FailureView> failed = all.stream()
                .filter(r -> r.getStatus() == TransactionStatus.FAILED)
                .sorted(Comparator.comparing(TransactionRecord::getTransactionId))
                .map(r -> new FailureView(r.getTransactionId(), r.getLastError()))
                .toList();

        List<AccountView> accountViews = accounts.findAll().stream()
                .sorted(Comparator.comparing(Account::getAccountId))
                .map(a -> AccountView.from(a, null))
                .toList();

        return new ProcessingSummary(clock.instant(), totalDeliveries, byOutcome, all.size(), byStatus,
                retried, retryAttempts, ledger.count(),
                deliveryViews(DeliveryOutcome.DUPLICATE), deliveryViews(DeliveryOutcome.CONFLICT),
                deliveryViews(DeliveryOutcome.REJECTED), failed, gaps(all), accountViews);
    }

    public List<TransactionView> transactions() {
        return records.findAll().stream()
                .sorted(Comparator.comparing((TransactionRecord r) -> String.valueOf(r.getAccountId()))
                        .thenComparing(r -> r.getSequenceNumber() == null ? Long.MAX_VALUE : r.getSequenceNumber())
                        .thenComparing(TransactionRecord::getTransactionId))
                .map(TransactionView::from)
                .toList();
    }

    public Optional<TransactionView> transaction(String transactionId) {
        return records.findById(transactionId).map(TransactionView::from);
    }

    public List<DeliveryView> deliveriesFor(String transactionId) {
        return deliveries.findByTransactionIdOrderByIdAsc(transactionId).stream().map(DeliveryView::from).toList();
    }

    public Optional<AccountView> account(String accountId) {
        return accounts.findById(accountId).map(a -> AccountView.from(a,
                ledger.findByAccountIdOrderByIdAsc(accountId).stream().map(LedgerView::from).toList()));
    }

    private List<DeliveryView> deliveryViews(DeliveryOutcome outcome) {
        return deliveries.findByOutcomeOrderByIdAsc(outcome).stream().map(DeliveryView::from).toList();
    }

    /** For each account with PENDING records, works out which sequence numbers are missing. */
    private List<GapView> gaps(List<TransactionRecord> all) {
        Map<String, List<TransactionRecord>> pendingByAccount = all.stream()
                .filter(r -> r.getStatus() == TransactionStatus.PENDING)
                .collect(Collectors.groupingBy(TransactionRecord::getAccountId, TreeMap::new, Collectors.toList()));
        Set<TransactionStatus> consumers = EnumSet.of(TransactionStatus.REJECTED, TransactionStatus.FAILED);

        List<GapView> gaps = new ArrayList<>();
        pendingByAccount.forEach((accountId, pending) -> {
            List<String> waiting = pending.stream()
                    .sorted(Comparator.comparing(TransactionRecord::getSequenceNumber))
                    .map(TransactionRecord::getTransactionId).toList();
            Optional<Account> account = accounts.findById(accountId);
            if (account.isEmpty()) {
                gaps.add(new GapView(accountId, List.of(), waiting, "New account: awaiting reorder window"));
                return;
            }
            Set<Long> consumed = all.stream()
                    .filter(r -> accountId.equals(r.getAccountId()) && consumers.contains(r.getStatus())
                            && r.getSequenceNumber() != null)
                    .map(TransactionRecord::getSequenceNumber).collect(Collectors.toSet());
            long firstWaiting = pending.stream().mapToLong(TransactionRecord::getSequenceNumber).min().orElseThrow();
            List<Long> missing = new ArrayList<>();
            for (long s = account.get().getLastAppliedSeq() + 1; s < firstWaiting && missing.size() < 100; s++) {
                if (!consumed.contains(s)) {
                    missing.add(s);
                }
            }
            gaps.add(new GapView(accountId, missing, waiting, "Waiting for missing sequence number(s)"));
        });
        return gaps;
    }
}
