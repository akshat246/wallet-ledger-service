package com.ledger.wallet.integration;

import com.ledger.wallet.dto.request.TransferRequest;
import com.ledger.wallet.dto.request.WithdrawalRequest;
import com.ledger.wallet.dto.response.TransactionResult;
import com.ledger.wallet.exception.InsufficientFundsException;
import com.ledger.wallet.service.ReconciliationService;
import com.ledger.wallet.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tests only a real database can run.
 *
 * <p>Each starts every thread from the same latch, so requests genuinely overlap rather than
 * queueing politely. All of these would pass against a mock, and most would pass against an
 * in-memory database, while the production system was still broken.
 */
class ConcurrencyIT extends AbstractIntegrationTest {

    @Autowired private ReconciliationService reconciliationService;

    private record Outcome<T>(T value, Exception error) {

        static <T> Outcome<T> success(T value) {
            return new Outcome<>(value, null);
        }

        static <T> Outcome<T> failure(Exception error) {
            return new Outcome<>(null, error);
        }

        boolean succeeded() {
            return error == null;
        }
    }

    /** Runs every task at once and returns all the outcomes, failures included. */
    private <T> List<Outcome<T>> runTogether(int threads, List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<Outcome<T>>> futures = new ArrayList<>();

        for (Callable<T> task : tasks) {
            futures.add(pool.submit(() -> {
                startGun.await();
                try {
                    return Outcome.success(task.call());
                } catch (Exception e) {
                    return Outcome.<T>failure(e);
                }
            }));
        }

        startGun.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS))
                .as("all requests finished; a timeout here would mean a deadlock")
                .isTrue();

        List<Outcome<T>> outcomes = new ArrayList<>();
        for (Future<Outcome<T>> future : futures) {
            outcomes.add(future.get());
        }
        return outcomes;
    }

    @Test
    @DisplayName("100 simultaneous withdrawals from a balance of 50 let exactly 50 through")
    void noOverdraftUnderConcurrency() throws Exception {
        UUID wallet = newFundedWallet("50.00");

        List<Callable<TransactionResult>> withdrawals = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            withdrawals.add(() -> transactionService.withdraw(key("w"),
                    new WithdrawalRequest(wallet, new BigDecimal("1.00"), null)));
        }

        List<Outcome<TransactionResult>> outcomes = runTogether(20, withdrawals);

        long succeeded = outcomes.stream().filter(Outcome::succeeded).count();
        long insufficient = outcomes.stream()
                .filter(o -> o.error() instanceof InsufficientFundsException)
                .count();

        // Without SELECT ... FOR UPDATE, two threads would both read 50, both pass the funds
        // check, and the wallet would go negative: the classic lost update.
        assertThat(succeeded).isEqualTo(50);
        assertThat(insufficient).isEqualTo(50);
        assertThat(balanceOf(wallet)).isEqualByComparingTo("0.00");
        assertThat(reconciliationService.reconcile(wallet).balanced()).isTrue();
    }

    @Test
    @DisplayName("transfers in both directions at once do not deadlock")
    void oppositeTransfersDoNotDeadlock() throws Exception {
        UUID a = newFundedWallet("1000.00");
        UUID b = newFundedWallet("1000.00");

        List<Callable<TransactionResult>> transfers = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            transfers.add(() -> transactionService.transfer(key("ab"),
                    new TransferRequest(a, b, new BigDecimal("10.00"), null)));
            transfers.add(() -> transactionService.transfer(key("ba"),
                    new TransferRequest(b, a, new BigDecimal("10.00"), null)));
        }

        List<Outcome<TransactionResult>> outcomes = runTogether(16, transfers);

        // Locking in ascending wallet id, rather than source-then-destination, is what stops
        // A->B and B->A from each holding the row the other needs.
        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(balanceOf(a)).isEqualByComparingTo("1000.00");
        assertThat(balanceOf(b)).isEqualByComparingTo("1000.00");
        assertThat(reconciliationService.reconcile(a).balanced()).isTrue();
        assertThat(reconciliationService.reconcile(b).balanced()).isTrue();
    }

    @Test
    @DisplayName("50 copies of one request with the same key produce a single transaction")
    void concurrentDuplicatesExecuteOnce() throws Exception {
        UUID from = newFundedWallet("500.00");
        UUID to = newWallet();
        String sharedKey = key("duplicate");
        TransferRequest request = new TransferRequest(from, to, new BigDecimal("100.00"), null);

        List<Callable<TransactionResult>> duplicates = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            duplicates.add(() -> transactionService.transfer(sharedKey, request));
        }

        List<Outcome<TransactionResult>> outcomes = runTogether(25, duplicates);

        AtomicInteger executed = new AtomicInteger();
        AtomicInteger replayed = new AtomicInteger();
        outcomes.stream().filter(Outcome::succeeded).forEach(outcome -> {
            if (outcome.value().replayed()) {
                replayed.incrementAndGet();
            } else {
                executed.incrementAndGet();
            }
        });

        // The idempotency key is the table's primary key, so the duplicates serialise on the
        // insert: one wins and the rest read back its result.
        assertThat(executed.get()).isEqualTo(1);
        assertThat(replayed.get()).isGreaterThan(0);
        assertThat(balanceOf(from)).isEqualByComparingTo("400.00");
        assertThat(balanceOf(to)).isEqualByComparingTo("100.00");

        Integer transactionRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM transactions WHERE idempotency_key = ?", Integer.class, sharedKey);
        assertThat(transactionRows).isEqualTo(1);
        assertThat(walletService.getLedger(to)).hasSize(1);
    }

    @Test
    @DisplayName("money is neither created nor destroyed under a mixed concurrent load")
    void totalMoneyIsConserved() throws Exception {
        UUID a = newFundedWallet("500.00");
        UUID b = newFundedWallet("500.00");
        UUID c = newFundedWallet("500.00");
        BigDecimal totalBefore = balanceOf(a).add(balanceOf(b)).add(balanceOf(c));

        List<Supplier<TransferRequest>> routes = List.of(
                () -> new TransferRequest(a, b, new BigDecimal("7.50"), null),
                () -> new TransferRequest(b, c, new BigDecimal("3.25"), null),
                () -> new TransferRequest(c, a, new BigDecimal("11.00"), null),
                () -> new TransferRequest(a, c, new BigDecimal("2.75"), null));

        List<Callable<TransactionResult>> work = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            Supplier<TransferRequest> route = routes.get(i % routes.size());
            work.add(() -> transactionService.transfer(key("mix"), route.get()));
        }

        runTogether(12, work);

        assertThat(balanceOf(a).add(balanceOf(b)).add(balanceOf(c)))
                .isEqualByComparingTo(totalBefore);
        assertThat(reconciliationService.reconcile(a).balanced()).isTrue();
        assertThat(reconciliationService.reconcile(b).balanced()).isTrue();
        assertThat(reconciliationService.reconcile(c).balanced()).isTrue();
    }
}
