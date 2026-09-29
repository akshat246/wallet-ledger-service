package com.natwest.wallet.service;

import com.natwest.wallet.domain.Transaction;
import com.natwest.wallet.dto.DepositRequest;
import com.natwest.wallet.dto.TransactionDTO;
import com.natwest.wallet.dto.TransactionResponse;
import com.natwest.wallet.dto.TransferRequest;
import com.natwest.wallet.dto.WithdrawalRequest;
import com.natwest.wallet.exception.TransactionException;
import com.natwest.wallet.exception.TransactionNotFoundException;
import com.natwest.wallet.exception.WalletLedgerException;
import com.natwest.wallet.exception.WalletNotFoundException;
import com.natwest.wallet.repository.TransactionRepository;
import com.natwest.wallet.repository.WalletRepository;
import com.natwest.wallet.service.IdempotencyService.ReplayedFailureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Entry point for money movement. Wraps {@link TransactionExecutor} with the idempotency
 * bookkeeping that has to happen <em>outside</em> the ledger transaction:
 * remembering business rejections, and resolving the loser of a concurrent-duplicate race.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionExecutor executor;
    private final IdempotencyService idempotencyService;
    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;

    public TransactionResponse transfer(String idempotencyKey, TransferRequest request) {
        String key = idempotencyService.requireKey(idempotencyKey);
        String hash = idempotencyService.fingerprint("TRANSFER",
                String.valueOf(request.getFromWalletId()),
                String.valueOf(request.getToWalletId()),
                request.getAmount().stripTrailingZeros().toPlainString());
        return runIdempotently(key, hash, () -> executor.transfer(key, hash, request));
    }

    public TransactionResponse deposit(String idempotencyKey, DepositRequest request) {
        String key = idempotencyService.requireKey(idempotencyKey);
        String hash = idempotencyService.fingerprint("DEPOSIT",
                String.valueOf(request.getToWalletId()),
                request.getAmount().stripTrailingZeros().toPlainString());
        return runIdempotently(key, hash, () -> executor.deposit(key, hash, request));
    }

    public TransactionResponse withdraw(String idempotencyKey, WithdrawalRequest request) {
        String key = idempotencyService.requireKey(idempotencyKey);
        String hash = idempotencyService.fingerprint("WITHDRAWAL",
                String.valueOf(request.getFromWalletId()),
                request.getAmount().stripTrailingZeros().toPlainString());
        return runIdempotently(key, hash, () -> executor.withdraw(key, hash, request));
    }

    @Transactional(readOnly = true)
    public TransactionDTO getTransaction(UUID transactionId) {
        return transactionRepository.findById(transactionId)
                .map(TransactionService::toDto)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    @Transactional(readOnly = true)
    public List<TransactionDTO> getWalletTransactions(UUID walletId) {
        if (!walletRepository.existsById(walletId)) {
            throw new WalletNotFoundException(walletId);
        }
        return transactionRepository.findWalletTransactions(walletId)
                .stream()
                .map(TransactionService::toDto)
                .toList();
    }

    /**
     * Runs the operation and handles the two things the executor's transaction cannot:
     * <ul>
     *   <li>A business rejection has rolled the transaction back, including the PENDING
     *       idempotency row - record the failure separately so a retry gets the same answer.</li>
     *   <li>Two requests with the same key arrived at once; the second lost the race on the
     *       idempotency primary key. Look up what the winner did and return that.</li>
     * </ul>
     */
    private TransactionResponse runIdempotently(String key, String hash, Supplier<TransactionResponse> operation) {
        try {
            return operation.get();
        } catch (ReplayedFailureException replayed) {
            throw replayed;
        } catch (WalletLedgerException businessFailure) {
            rememberFailure(key, hash, businessFailure);
            throw businessFailure;
        } catch (DataIntegrityViolationException raceLost) {
            log.info("Concurrent duplicate for idempotency key {}; returning the winner's result", key);
            Optional<TransactionResponse> winner = idempotencyService.replay(key, hash);
            return winner.orElseThrow(() -> new TransactionException(
                    "A request with this Idempotency-Key is still being processed. Retry shortly."));
        }
    }

    private void rememberFailure(String key, String hash, WalletLedgerException failure) {
        try {
            idempotencyService.recordFailure(key, hash, failure);
        } catch (RuntimeException e) {
            // Best effort: losing the failure record only means a retry is re-validated.
            log.warn("Could not record failed idempotency key {}: {}", key, e.getMessage());
        }
    }

    static TransactionDTO toDto(Transaction transaction) {
        return TransactionDTO.builder()
                .id(transaction.getId())
                .idempotencyKey(transaction.getIdempotencyKey())
                .fromWalletId(transaction.getFromWallet() != null ? transaction.getFromWallet().getId() : null)
                .toWalletId(transaction.getToWallet() != null ? transaction.getToWallet().getId() : null)
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .type(transaction.getType().name())
                .status(transaction.getStatus().name())
                .failureReason(transaction.getFailureReason())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }
}
