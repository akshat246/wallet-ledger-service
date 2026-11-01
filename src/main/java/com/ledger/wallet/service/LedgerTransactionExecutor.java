package com.ledger.wallet.service;

import com.ledger.wallet.domain.EntryType;
import com.ledger.wallet.domain.LedgerEntry;
import com.ledger.wallet.domain.Transaction;
import com.ledger.wallet.domain.TransactionType;
import com.ledger.wallet.domain.Wallet;
import com.ledger.wallet.dto.request.DepositRequest;
import com.ledger.wallet.dto.request.TransferRequest;
import com.ledger.wallet.dto.request.WithdrawalRequest;
import com.ledger.wallet.dto.response.TransactionResult;
import com.ledger.wallet.exception.CurrencyMismatchException;
import com.ledger.wallet.exception.InsufficientFundsException;
import com.ledger.wallet.exception.InvalidAmountException;
import com.ledger.wallet.exception.SameWalletTransferException;
import com.ledger.wallet.exception.WalletNotActiveException;
import com.ledger.wallet.exception.WalletNotFoundException;
import com.ledger.wallet.mapper.TransactionMapper;
import com.ledger.wallet.repository.LedgerEntryRepository;
import com.ledger.wallet.repository.TransactionRepository;
import com.ledger.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

/**
 * Performs one money movement as a single database transaction:
 *
 * <pre>
 *   claim idempotency key -&gt; lock wallet rows (SELECT ... FOR UPDATE)
 *   -&gt; validate           -&gt; update balances
 *   -&gt; insert transaction -&gt; insert DEBIT / CREDIT ledger rows
 *   -&gt; mark key SUCCESS   -&gt; commit
 * </pre>
 *
 * Anything thrown rolls all of it back together, so a balance can never drift from its
 * ledger and a key can never read SUCCESS for money that did not move.
 *
 * <p>Two ordering decisions matter. Wallets are locked in ascending id order, so a transfer
 * A to B and a simultaneous B to A queue instead of deadlocking. And validation happens
 * <em>after</em> the lock, because a balance read before locking may be stale by the time
 * the write happens.
 *
 * <p>This is a separate bean from {@link TransactionService} deliberately: Spring applies
 * {@code @Transactional} through a proxy, so a rollback only becomes observable to a caller
 * outside this class. {@code TransactionService} is that caller.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LedgerTransactionExecutor {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final IdempotencyService idempotencyService;

    @Transactional
    public TransactionResult transfer(String key, String requestHash, TransferRequest request) {
        Optional<TransactionResult> replay = idempotencyService.claim(key, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        UUID fromId = request.fromWalletId();
        UUID toId = request.toWalletId();
        if (fromId.equals(toId)) {
            throw new SameWalletTransferException();
        }
        BigDecimal amount = normalise(request.amount());

        // Consistent lock order: both directions of a transfer pair take min(id) first.
        boolean fromIsLower = fromId.compareTo(toId) < 0;
        Wallet first = lockWallet(fromIsLower ? fromId : toId);
        Wallet second = lockWallet(fromIsLower ? toId : fromId);
        Wallet from = fromIsLower ? first : second;
        Wallet to = fromIsLower ? second : first;

        requireActive(from);
        requireActive(to);
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new CurrencyMismatchException(from.getCurrency(), to.getCurrency());
        }
        requireFunds(from, amount);

        Transaction transaction = transactionRepository.save(Transaction.builder()
                .idempotencyKey(key)
                .fromWallet(from)
                .toWallet(to)
                .amount(amount)
                .currency(from.getCurrency())
                .type(TransactionType.TRANSFER)
                .build());

        from.debit(amount);
        to.credit(amount);
        walletRepository.save(from);
        walletRepository.save(to);

        String description = request.description();
        record(from, transaction, EntryType.DEBIT, amount, describe(description, "Transfer to " + to.getId()));
        record(to, transaction, EntryType.CREDIT, amount, describe(description, "Transfer from " + from.getId()));

        transaction.markCompleted();
        TransactionResult result = TransactionMapper.toResult(transaction);
        idempotencyService.complete(key, result);

        log.info("Transfer {} completed: {} {} from {} to {}",
                transaction.getId(), amount, from.getCurrency(), from.getId(), to.getId());
        return result;
    }

    @Transactional
    public TransactionResult deposit(String key, String requestHash, DepositRequest request) {
        Optional<TransactionResult> replay = idempotencyService.claim(key, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        BigDecimal amount = normalise(request.amount());
        Wallet wallet = lockWallet(request.toWalletId());
        requireActive(wallet);

        Transaction transaction = transactionRepository.save(Transaction.builder()
                .idempotencyKey(key)
                .toWallet(wallet)
                .amount(amount)
                .currency(wallet.getCurrency())
                .type(TransactionType.DEPOSIT)
                .build());

        wallet.credit(amount);
        walletRepository.save(wallet);
        record(wallet, transaction, EntryType.CREDIT, amount, describe(request.description(), "Deposit"));

        transaction.markCompleted();
        TransactionResult result = TransactionMapper.toResult(transaction);
        idempotencyService.complete(key, result);

        log.info("Deposit {} completed: {} {} into {}",
                transaction.getId(), amount, wallet.getCurrency(), wallet.getId());
        return result;
    }

    @Transactional
    public TransactionResult withdraw(String key, String requestHash, WithdrawalRequest request) {
        Optional<TransactionResult> replay = idempotencyService.claim(key, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        BigDecimal amount = normalise(request.amount());
        Wallet wallet = lockWallet(request.fromWalletId());
        requireActive(wallet);
        requireFunds(wallet, amount);

        Transaction transaction = transactionRepository.save(Transaction.builder()
                .idempotencyKey(key)
                .fromWallet(wallet)
                .amount(amount)
                .currency(wallet.getCurrency())
                .type(TransactionType.WITHDRAWAL)
                .build());

        wallet.debit(amount);
        walletRepository.save(wallet);
        record(wallet, transaction, EntryType.DEBIT, amount, describe(request.description(), "Withdrawal"));

        transaction.markCompleted();
        TransactionResult result = TransactionMapper.toResult(transaction);
        idempotencyService.complete(key, result);

        log.info("Withdrawal {} completed: {} {} from {}",
                transaction.getId(), amount, wallet.getCurrency(), wallet.getId());
        return result;
    }

    private Wallet lockWallet(UUID walletId) {
        return walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }

    /** Writes one side of the double entry, stamped with the balance after the movement. */
    private void record(Wallet wallet, Transaction transaction, EntryType type,
                        BigDecimal amount, String description) {
        ledgerEntryRepository.save(LedgerEntry.builder()
                .wallet(wallet)
                .transaction(transaction)
                .entryType(type)
                .amount(amount)
                .runningBalance(wallet.getBalance())
                .description(description)
                .build());
    }

    private static void requireActive(Wallet wallet) {
        if (!wallet.isActive()) {
            throw new WalletNotActiveException(wallet.getId(), wallet.getStatus().name());
        }
    }

    private static void requireFunds(Wallet wallet, BigDecimal amount) {
        if (!wallet.hasSufficientBalance(amount)) {
            throw new InsufficientFundsException(wallet.getId(), wallet.getBalance(), amount);
        }
    }

    /** Bean validation already rejected nulls and non-positive amounts; this is the backstop. */
    private static BigDecimal normalise(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidAmountException(amount);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static String describe(String clientDescription, String fallback) {
        return (clientDescription == null || clientDescription.isBlank())
                ? fallback
                : clientDescription.trim();
    }
}
