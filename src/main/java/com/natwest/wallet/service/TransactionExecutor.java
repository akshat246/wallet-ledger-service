package com.natwest.wallet.service;

import com.natwest.wallet.domain.EntryType;
import com.natwest.wallet.domain.LedgerEntry;
import com.natwest.wallet.domain.Transaction;
import com.natwest.wallet.domain.TransactionType;
import com.natwest.wallet.domain.Wallet;
import com.natwest.wallet.dto.DepositRequest;
import com.natwest.wallet.dto.TransactionResponse;
import com.natwest.wallet.dto.TransferRequest;
import com.natwest.wallet.dto.WithdrawalRequest;
import com.natwest.wallet.exception.CurrencyMismatchException;
import com.natwest.wallet.exception.InsufficientFundsException;
import com.natwest.wallet.exception.InvalidAmountException;
import com.natwest.wallet.exception.SameWalletTransferException;
import com.natwest.wallet.exception.WalletNotActiveException;
import com.natwest.wallet.exception.WalletNotFoundException;
import com.natwest.wallet.repository.LedgerEntryRepository;
import com.natwest.wallet.repository.TransactionRepository;
import com.natwest.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Executes a single money movement as one database transaction:
 *
 * <pre>
 *   claim idempotency key  ->  lock wallet rows (SELECT ... FOR UPDATE)
 *   -> validate            ->  update balances
 *   -> insert transaction  ->  insert DEBIT / CREDIT ledger entries
 *   -> mark key SUCCESS    ->  commit
 * </pre>
 *
 * Any exception rolls the whole thing back, so a wallet balance can never diverge from
 * its ledger. Wallets are always locked in ascending UUID order so two opposite transfers
 * (A->B and B->A) running at the same time cannot deadlock.
 *
 * <p>This is a separate bean from {@link TransactionService} on purpose: Spring's
 * {@code @Transactional} only applies to calls that go through the proxy, and the
 * service needs to react to a rollback (record the failure) from outside the transaction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionExecutor {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final IdempotencyService idempotencyService;

    @Transactional
    public TransactionResponse transfer(String key, String requestHash, TransferRequest request) {
        Optional<TransactionResponse> replay = idempotencyService.claim(key, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        UUID fromId = request.getFromWalletId();
        UUID toId = request.getToWalletId();
        if (fromId.equals(toId)) {
            throw new SameWalletTransferException();
        }
        BigDecimal amount = normalise(request.getAmount());

        // Deterministic lock order prevents deadlocks between concurrent opposite transfers.
        boolean fromFirst = fromId.compareTo(toId) < 0;
        Wallet first = lockWallet(fromFirst ? fromId : toId);
        Wallet second = lockWallet(fromFirst ? toId : fromId);
        Wallet from = fromFirst ? first : second;
        Wallet to = fromFirst ? second : first;

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

        String description = describe(request.getDescription(), "Transfer to " + to.getId());
        ledgerEntryRepository.save(entry(from, transaction, EntryType.DEBIT, amount, description));
        ledgerEntryRepository.save(entry(to, transaction, EntryType.CREDIT, amount,
                describe(request.getDescription(), "Transfer from " + from.getId())));

        transaction.markCompleted();
        TransactionResponse response = toResponse(transaction);
        idempotencyService.complete(key, response);

        log.info("Transfer {} completed: {} {} from {} to {}", transaction.getId(), amount, from.getCurrency(), from.getId(), to.getId());
        return response;
    }

    @Transactional
    public TransactionResponse deposit(String key, String requestHash, DepositRequest request) {
        Optional<TransactionResponse> replay = idempotencyService.claim(key, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        BigDecimal amount = normalise(request.getAmount());
        Wallet wallet = lockWallet(request.getToWalletId());
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
        ledgerEntryRepository.save(entry(wallet, transaction, EntryType.CREDIT, amount,
                describe(request.getDescription(), "Deposit")));

        transaction.markCompleted();
        TransactionResponse response = toResponse(transaction);
        idempotencyService.complete(key, response);

        log.info("Deposit {} completed: {} {} to {}", transaction.getId(), amount, wallet.getCurrency(), wallet.getId());
        return response;
    }

    @Transactional
    public TransactionResponse withdraw(String key, String requestHash, WithdrawalRequest request) {
        Optional<TransactionResponse> replay = idempotencyService.claim(key, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        BigDecimal amount = normalise(request.getAmount());
        Wallet wallet = lockWallet(request.getFromWalletId());
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
        ledgerEntryRepository.save(entry(wallet, transaction, EntryType.DEBIT, amount,
                describe(request.getDescription(), "Withdrawal")));

        transaction.markCompleted();
        TransactionResponse response = toResponse(transaction);
        idempotencyService.complete(key, response);

        log.info("Withdrawal {} completed: {} {} from {}", transaction.getId(), amount, wallet.getCurrency(), wallet.getId());
        return response;
    }

    // ---------------------------------------------------------------- helpers

    private Wallet lockWallet(UUID walletId) {
        return walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }

    private static void requireActive(Wallet wallet) {
        if (!wallet.isActive()) {
            throw new WalletNotActiveException(wallet.getId(),
                    wallet.getStatus() != null ? wallet.getStatus().name() : "UNKNOWN");
        }
    }

    private static void requireFunds(Wallet wallet, BigDecimal amount) {
        if (!wallet.hasSufficientBalance(amount)) {
            throw new InsufficientFundsException(wallet.getId(), wallet.getBalance(), amount);
        }
    }

    private static BigDecimal normalise(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidAmountException(amount);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static String describe(String clientDescription, String fallback) {
        return (clientDescription == null || clientDescription.isBlank()) ? fallback : clientDescription.trim();
    }

    private static LedgerEntry entry(Wallet wallet, Transaction transaction, EntryType type,
                                     BigDecimal amount, String description) {
        return LedgerEntry.builder()
                .wallet(wallet)
                .transaction(transaction)
                .entryType(type)
                .amount(amount)
                .runningBalance(wallet.getBalance())   // balance after this entry was applied
                .description(description)
                .build();
    }

    static TransactionResponse toResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .transactionId(transaction.getId())
                .idempotencyKey(transaction.getIdempotencyKey())
                .type(transaction.getType().name())
                .status(transaction.getStatus().name())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .fromWalletId(transaction.getFromWallet() != null ? transaction.getFromWallet().getId() : null)
                .toWalletId(transaction.getToWallet() != null ? transaction.getToWallet().getId() : null)
                .failureReason(transaction.getFailureReason())
                .replayed(false)
                .timestamp(transaction.getCreatedAt() != null ? transaction.getCreatedAt() : LocalDateTime.now())
                .build();
    }
}
