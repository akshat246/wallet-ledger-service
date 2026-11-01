package com.ledger.wallet.service;

import com.ledger.wallet.dto.request.DepositRequest;
import com.ledger.wallet.dto.request.TransferRequest;
import com.ledger.wallet.dto.request.WithdrawalRequest;
import com.ledger.wallet.dto.response.TransactionResponse;
import com.ledger.wallet.dto.response.TransactionResult;
import com.ledger.wallet.exception.ReplayedFailureException;
import com.ledger.wallet.exception.TransactionException;
import com.ledger.wallet.exception.TransactionNotFoundException;
import com.ledger.wallet.exception.WalletLedgerException;
import com.ledger.wallet.exception.WalletNotFoundException;
import com.ledger.wallet.mapper.TransactionMapper;
import com.ledger.wallet.repository.TransactionRepository;
import com.ledger.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The entry point for money movement.
 *
 * <p>It wraps {@link LedgerTransactionExecutor} with the two pieces of idempotency
 * bookkeeping that cannot happen inside the ledger transaction, because they only make
 * sense once that transaction has ended.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final LedgerTransactionExecutor executor;
    private final IdempotencyService idempotencyService;
    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;

    public TransactionResult transfer(String idempotencyKey, TransferRequest request) {
        String key = idempotencyService.requireKey(idempotencyKey);
        String hash = idempotencyService.fingerprint("TRANSFER",
                String.valueOf(request.fromWalletId()),
                String.valueOf(request.toWalletId()),
                plain(request.amount()));
        return execute(key, hash, () -> executor.transfer(key, hash, request));
    }

    public TransactionResult deposit(String idempotencyKey, DepositRequest request) {
        String key = idempotencyService.requireKey(idempotencyKey);
        String hash = idempotencyService.fingerprint("DEPOSIT",
                String.valueOf(request.toWalletId()),
                plain(request.amount()));
        return execute(key, hash, () -> executor.deposit(key, hash, request));
    }

    public TransactionResult withdraw(String idempotencyKey, WithdrawalRequest request) {
        String key = idempotencyService.requireKey(idempotencyKey);
        String hash = idempotencyService.fingerprint("WITHDRAWAL",
                String.valueOf(request.fromWalletId()),
                plain(request.amount()));
        return execute(key, hash, () -> executor.withdraw(key, hash, request));
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(UUID transactionId) {
        return transactionRepository.findById(transactionId)
                .map(TransactionMapper::toResponse)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getWalletTransactions(UUID walletId) {
        if (!walletRepository.existsById(walletId)) {
            throw new WalletNotFoundException(walletId);
        }
        return transactionRepository.findByWalletId(walletId).stream()
                .map(TransactionMapper::toResponse)
                .toList();
    }

    /**
     * Runs the movement and handles the two outcomes its own transaction cannot:
     *
     * <ul>
     *   <li>A rejection has rolled the transaction back, taking the PENDING idempotency row
     *       with it. The rejection is recorded separately so a retry of the same request
     *       receives the same answer rather than a different one.</li>
     *   <li>Two requests with the same key arrived together and this one lost the race on
     *       the primary key. The winner's stored outcome is returned instead.</li>
     * </ul>
     */
    private TransactionResult execute(String key, String hash, Supplier<TransactionResult> movement) {
        try {
            return movement.get();
        } catch (ReplayedFailureException alreadyRecorded) {
            throw alreadyRecorded;
        } catch (WalletLedgerException rejected) {
            rememberRejection(key, hash, rejected);
            throw rejected;
        } catch (DataIntegrityViolationException lostTheRace) {
            log.info("Concurrent duplicate of idempotency key {}; returning the winner's result", key);
            return idempotencyService.lookup(key, hash)
                    .orElseThrow(() -> new TransactionException(
                            "A request with this Idempotency-Key is still in flight. Retry shortly."));
        }
    }

    private void rememberRejection(String key, String hash, WalletLedgerException rejection) {
        try {
            idempotencyService.recordFailure(key, hash, rejection);
        } catch (RuntimeException e) {
            // Best effort. Losing the record only means a retry is re-validated from scratch,
            // which is still correct, just not identical.
            log.warn("Could not record rejected idempotency key {}: {}", key, e.getMessage());
        }
    }

    private static String plain(BigDecimal amount) {
        return amount == null ? "null" : amount.stripTrailingZeros().toPlainString();
    }
}
