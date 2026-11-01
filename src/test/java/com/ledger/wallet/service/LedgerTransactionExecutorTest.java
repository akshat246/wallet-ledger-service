package com.ledger.wallet.service;

import com.ledger.wallet.domain.EntryType;
import com.ledger.wallet.domain.LedgerEntry;
import com.ledger.wallet.domain.Transaction;
import com.ledger.wallet.domain.TransactionType;
import com.ledger.wallet.domain.Wallet;
import com.ledger.wallet.domain.WalletStatus;
import com.ledger.wallet.dto.request.DepositRequest;
import com.ledger.wallet.dto.request.TransferRequest;
import com.ledger.wallet.dto.request.WithdrawalRequest;
import com.ledger.wallet.dto.response.TransactionResult;
import com.ledger.wallet.exception.CurrencyMismatchException;
import com.ledger.wallet.exception.InsufficientFundsException;
import com.ledger.wallet.exception.SameWalletTransferException;
import com.ledger.wallet.exception.WalletNotActiveException;
import com.ledger.wallet.exception.WalletNotFoundException;
import com.ledger.wallet.repository.LedgerEntryRepository;
import com.ledger.wallet.repository.TransactionRepository;
import com.ledger.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The arithmetic and the rules of a single movement, in isolation: what is debited, what is
 * written to the ledger, and which requests are rejected before anything moves.
 *
 * <p>What these cannot cover is anything depending on a real database — locking, rollback,
 * concurrent duplicates. Those live in the integration tests.
 */
@ExtendWith(MockitoExtension.class)
class LedgerTransactionExecutorTest {

    @Mock private WalletRepository walletRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private IdempotencyService idempotencyService;

    private LedgerTransactionExecutor executor;

    private static final String KEY = "key-1";
    private static final String HASH = "hash-1";

    @BeforeEach
    void setUp() {
        executor = new LedgerTransactionExecutor(
                walletRepository, transactionRepository, ledgerEntryRepository, idempotencyService);
    }

    private static Wallet wallet(UUID id, String balance, String currency, WalletStatus status) {
        return Wallet.builder()
                .id(id)
                .balance(new BigDecimal(balance))
                .currency(currency)
                .status(status)
                .walletType("PRIMARY")
                .build();
    }

    private static Wallet activeWallet(UUID id, String balance) {
        return wallet(id, balance, "INR", WalletStatus.ACTIVE);
    }

    /** The key is unclaimed, so the movement proceeds. */
    private void keyIsNew() {
        when(idempotencyService.claim(anyString(), anyString())).thenReturn(Optional.empty());
    }

    private void walletsExist(Wallet... wallets) {
        for (Wallet w : wallets) {
            when(walletRepository.findByIdForUpdate(w.getId())).thenReturn(Optional.of(w));
        }
    }

    private void transactionsAreSavedAsGiven() {
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    @DisplayName("transfer")
    class Transfer {

        @Test
        @DisplayName("moves the amount from one balance to the other")
        void movesMoney() {
            UUID fromId = UUID.randomUUID();
            UUID toId = UUID.randomUUID();
            Wallet from = activeWallet(fromId, "100.00");
            Wallet to = activeWallet(toId, "25.00");
            keyIsNew();
            walletsExist(from, to);
            transactionsAreSavedAsGiven();

            TransactionResult result = executor.transfer(KEY, HASH,
                    new TransferRequest(fromId, toId, new BigDecimal("30.00"), "Dinner"));

            assertThat(from.getBalance()).isEqualByComparingTo("70.00");
            assertThat(to.getBalance()).isEqualByComparingTo("55.00");
            assertThat(result.type()).isEqualTo(TransactionType.TRANSFER.name());
            assertThat(result.status()).isEqualTo("COMPLETED");
            assertThat(result.replayed()).isFalse();
            verify(idempotencyService).complete(eq(KEY), any(TransactionResult.class));
        }

        @Test
        @DisplayName("writes one DEBIT and one CREDIT carrying the balance after the movement")
        void writesBothSidesOfTheLedger() {
            UUID fromId = UUID.randomUUID();
            UUID toId = UUID.randomUUID();
            Wallet from = activeWallet(fromId, "100.00");
            Wallet to = activeWallet(toId, "25.00");
            keyIsNew();
            walletsExist(from, to);
            transactionsAreSavedAsGiven();

            executor.transfer(KEY, HASH,
                    new TransferRequest(fromId, toId, new BigDecimal("30.00"), null));

            ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
            verify(ledgerEntryRepository, times(2)).save(captor.capture());
            List<LedgerEntry> entries = captor.getAllValues();

            LedgerEntry debit = entries.stream().filter(LedgerEntry::isDebit).findFirst().orElseThrow();
            LedgerEntry credit = entries.stream().filter(LedgerEntry::isCredit).findFirst().orElseThrow();

            assertThat(debit.getWallet().getId()).isEqualTo(fromId);
            assertThat(debit.getAmount()).isEqualByComparingTo("30.00");
            assertThat(debit.getRunningBalance()).isEqualByComparingTo("70.00");

            assertThat(credit.getWallet().getId()).isEqualTo(toId);
            assertThat(credit.getAmount()).isEqualByComparingTo("30.00");
            assertThat(credit.getRunningBalance()).isEqualByComparingTo("55.00");

            // Double entry: the two sides cancel, so no money is created or destroyed.
            assertThat(debit.signedAmount().add(credit.signedAmount())).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("locks the same wallet first whichever direction the money goes")
        void locksInAConsistentOrder() {
            // A->B and a simultaneous B->A would deadlock if each locked its own source first.
            // What prevents that is not any particular order, only that both directions use the
            // same one. (Which id counts as "first" is an implementation detail: UUID.compareTo
            // compares signed longs, so it disagrees with both string order and Postgres order.)
            UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID b = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
            keyIsNew();
            walletsExist(activeWallet(a, "100.00"), activeWallet(b, "100.00"));
            transactionsAreSavedAsGiven();

            executor.transfer("k-ab", HASH, new TransferRequest(a, b, new BigDecimal("10.00"), null));
            executor.transfer("k-ba", HASH, new TransferRequest(b, a, new BigDecimal("10.00"), null));

            ArgumentCaptor<UUID> locked = ArgumentCaptor.forClass(UUID.class);
            verify(walletRepository, times(4)).findByIdForUpdate(locked.capture());
            List<UUID> order = locked.getAllValues();

            assertThat(order.get(0)).as("first lock taken by A->B").isEqualTo(order.get(2));
            assertThat(order.get(1)).as("second lock taken by A->B").isEqualTo(order.get(3));
            assertThat(order.get(0)).isNotEqualTo(order.get(1));
        }

        @Test
        @DisplayName("rejects an overdraft without touching either balance")
        void rejectsOverdraft() {
            UUID fromId = UUID.randomUUID();
            UUID toId = UUID.randomUUID();
            Wallet from = activeWallet(fromId, "10.00");
            Wallet to = activeWallet(toId, "0.00");
            keyIsNew();
            walletsExist(from, to);

            assertThatThrownBy(() -> executor.transfer(KEY, HASH,
                    new TransferRequest(fromId, toId, new BigDecimal("10.01"), null)))
                    .isInstanceOf(InsufficientFundsException.class);

            assertThat(from.getBalance()).isEqualByComparingTo("10.00");
            assertThat(to.getBalance()).isEqualByComparingTo("0.00");
            verify(ledgerEntryRepository, never()).save(any());
            verify(idempotencyService, never()).complete(anyString(), any());
        }

        @Test
        @DisplayName("rejects a transfer to the same wallet before taking any lock")
        void rejectsSelfTransfer() {
            UUID walletId = UUID.randomUUID();
            keyIsNew();

            assertThatThrownBy(() -> executor.transfer(KEY, HASH,
                    new TransferRequest(walletId, walletId, new BigDecimal("5.00"), null)))
                    .isInstanceOf(SameWalletTransferException.class);

            verify(walletRepository, never()).findByIdForUpdate(any());
        }

        @Test
        @DisplayName("rejects a frozen source wallet")
        void rejectsFrozenWallet() {
            UUID fromId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID toId = UUID.fromString("00000000-0000-0000-0000-000000000002");
            Wallet from = wallet(fromId, "100.00", "INR", WalletStatus.FROZEN);
            Wallet to = activeWallet(toId, "0.00");
            keyIsNew();
            walletsExist(from, to);

            assertThatThrownBy(() -> executor.transfer(KEY, HASH,
                    new TransferRequest(fromId, toId, new BigDecimal("5.00"), null)))
                    .isInstanceOf(WalletNotActiveException.class);
        }

        @Test
        @DisplayName("refuses to move money between different currencies")
        void rejectsCurrencyMismatch() {
            UUID fromId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID toId = UUID.fromString("00000000-0000-0000-0000-000000000002");
            Wallet from = wallet(fromId, "100.00", "INR", WalletStatus.ACTIVE);
            Wallet to = wallet(toId, "0.00", "USD", WalletStatus.ACTIVE);
            keyIsNew();
            walletsExist(from, to);

            assertThatThrownBy(() -> executor.transfer(KEY, HASH,
                    new TransferRequest(fromId, toId, new BigDecimal("5.00"), null)))
                    .isInstanceOf(CurrencyMismatchException.class);
        }

        @Test
        @DisplayName("fails when the destination wallet does not exist")
        void rejectsUnknownWallet() {
            UUID fromId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID missing = UUID.fromString("00000000-0000-0000-0000-000000000002");
            keyIsNew();
            when(walletRepository.findByIdForUpdate(fromId))
                    .thenReturn(Optional.of(activeWallet(fromId, "100.00")));
            when(walletRepository.findByIdForUpdate(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> executor.transfer(KEY, HASH,
                    new TransferRequest(fromId, missing, new BigDecimal("5.00"), null)))
                    .isInstanceOf(WalletNotFoundException.class);
        }

        @Test
        @DisplayName("returns the stored result instead of moving money when the key is known")
        void replaysAClaimedKey() {
            TransactionResult stored = new TransactionResult(UUID.randomUUID(), KEY, "TRANSFER",
                    "COMPLETED", new BigDecimal("30.00"), "INR", UUID.randomUUID(), UUID.randomUUID(),
                    true, null);
            when(idempotencyService.claim(KEY, HASH)).thenReturn(Optional.of(stored));

            TransactionResult result = executor.transfer(KEY, HASH,
                    new TransferRequest(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("30.00"), null));

            assertThat(result.replayed()).isTrue();
            verify(walletRepository, never()).findByIdForUpdate(any());
            verify(transactionRepository, never()).save(any());
            verify(ledgerEntryRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deposit and withdrawal")
    class SingleSided {

        @Test
        @DisplayName("a deposit credits the wallet and writes one CREDIT entry")
        void depositCredits() {
            UUID walletId = UUID.randomUUID();
            Wallet wallet = activeWallet(walletId, "10.00");
            keyIsNew();
            walletsExist(wallet);
            transactionsAreSavedAsGiven();

            executor.deposit(KEY, HASH, new DepositRequest(walletId, new BigDecimal("40.00"), null));

            assertThat(wallet.getBalance()).isEqualByComparingTo("50.00");

            ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
            verify(ledgerEntryRepository).save(captor.capture());
            assertThat(captor.getValue().getEntryType()).isEqualTo(EntryType.CREDIT);
            assertThat(captor.getValue().getRunningBalance()).isEqualByComparingTo("50.00");
        }

        @Test
        @DisplayName("a withdrawal debits the wallet and writes one DEBIT entry")
        void withdrawalDebits() {
            UUID walletId = UUID.randomUUID();
            Wallet wallet = activeWallet(walletId, "50.00");
            keyIsNew();
            walletsExist(wallet);
            transactionsAreSavedAsGiven();

            executor.withdraw(KEY, HASH, new WithdrawalRequest(walletId, new BigDecimal("20.00"), null));

            assertThat(wallet.getBalance()).isEqualByComparingTo("30.00");

            ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
            verify(ledgerEntryRepository).save(captor.capture());
            assertThat(captor.getValue().getEntryType()).isEqualTo(EntryType.DEBIT);
            assertThat(captor.getValue().getRunningBalance()).isEqualByComparingTo("30.00");
        }

        @Test
        @DisplayName("rounds a sub-paisa amount to two decimal places")
        void roundsToScaleTwo() {
            UUID walletId = UUID.randomUUID();
            Wallet wallet = activeWallet(walletId, "0.00");
            keyIsNew();
            walletsExist(wallet);
            transactionsAreSavedAsGiven();

            executor.deposit(KEY, HASH, new DepositRequest(walletId, new BigDecimal("10.005"), null));

            assertThat(wallet.getBalance()).isEqualByComparingTo("10.01");
        }

        @Test
        @DisplayName("a withdrawal larger than the balance is rejected")
        void withdrawalCannotOverdraw() {
            UUID walletId = UUID.randomUUID();
            Wallet wallet = activeWallet(walletId, "5.00");
            keyIsNew();
            walletsExist(wallet);

            assertThatThrownBy(() -> executor.withdraw(KEY, HASH,
                    new WithdrawalRequest(walletId, new BigDecimal("5.01"), null)))
                    .isInstanceOf(InsufficientFundsException.class);

            assertThat(wallet.getBalance()).isEqualByComparingTo("5.00");
        }
    }
}