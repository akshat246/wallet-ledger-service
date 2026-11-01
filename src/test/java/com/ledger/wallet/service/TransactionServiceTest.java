package com.ledger.wallet.service;

import com.ledger.wallet.dto.request.TransferRequest;
import com.ledger.wallet.dto.response.TransactionResult;
import com.ledger.wallet.exception.InsufficientFundsException;
import com.ledger.wallet.exception.ReplayedFailureException;
import com.ledger.wallet.exception.TransactionException;
import com.ledger.wallet.exception.WalletLedgerException;
import com.ledger.wallet.repository.TransactionRepository;
import com.ledger.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * By the time these paths run, the executor's transaction has already ended, which is the
 * whole reason this layer exists. These tests pin down what it does with each outcome.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private LedgerTransactionExecutor executor;
    @Mock private IdempotencyService idempotencyService;
    @Mock private TransactionRepository transactionRepository;
    @Mock private WalletRepository walletRepository;

    private TransactionService service;

    private final TransferRequest request = new TransferRequest(
            UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.00"), null);

    @BeforeEach
    void setUp() {
        service = new TransactionService(executor, idempotencyService, transactionRepository, walletRepository);
        when(idempotencyService.requireKey(anyString())).thenAnswer(i -> i.getArgument(0));
        when(idempotencyService.fingerprint(any(String[].class))).thenReturn("hash");
    }

    @Test
    @DisplayName("records a rejection so a retry gets the same answer")
    void recordsRejections() {
        InsufficientFundsException rejection = new InsufficientFundsException(
                UUID.randomUUID(), new BigDecimal("1.00"), new BigDecimal("10.00"));
        when(executor.transfer(anyString(), anyString(), any())).thenThrow(rejection);

        assertThatThrownBy(() -> service.transfer("key-1", request)).isSameAs(rejection);

        // Without this, a retry after a deposit landed would quietly succeed and the client
        // would have seen two different answers to one request.
        verify(idempotencyService).recordFailure("key-1", "hash", rejection);
    }

    @Test
    @DisplayName("does not re-record a rejection that was already stored")
    void doesNotRecordReplayedFailures() {
        ReplayedFailureException replayed = new ReplayedFailureException(
                "Insufficient funds", "INSUFFICIENT_FUNDS", HttpStatus.UNPROCESSABLE_ENTITY);
        when(executor.transfer(anyString(), anyString(), any())).thenThrow(replayed);

        assertThatThrownBy(() -> service.transfer("key-1", request)).isSameAs(replayed);

        verify(idempotencyService, never()).recordFailure(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("still fails the request if the rejection could not be recorded")
    void recordingFailureDoesNotMaskTheRejection() {
        InsufficientFundsException rejection = new InsufficientFundsException(
                UUID.randomUUID(), BigDecimal.ZERO, BigDecimal.ONE);
        when(executor.transfer(anyString(), anyString(), any())).thenThrow(rejection);
        Mockito.doThrow(new IllegalStateException("database is down"))
                .when(idempotencyService).recordFailure(anyString(), anyString(), any());

        assertThatThrownBy(() -> service.transfer("key-1", request)).isSameAs(rejection);
    }

    @Test
    @DisplayName("returns the winner's result when a concurrent duplicate lost the race")
    void resolvesConcurrentDuplicates() {
        // Two requests with the same key: this one's insert hit the primary key and rolled back.
        when(executor.transfer(anyString(), anyString(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value"));
        TransactionResult winner = new TransactionResult(UUID.randomUUID(), "key-1", "TRANSFER",
                "COMPLETED", new BigDecimal("10.00"), "INR", UUID.randomUUID(), UUID.randomUUID(),
                true, null);
        when(idempotencyService.lookup("key-1", "hash")).thenReturn(Optional.of(winner));

        TransactionResult result = service.transfer("key-1", request);

        assertThat(result).isSameAs(winner);
        assertThat(result.replayed()).isTrue();
    }

    @Test
    @DisplayName("asks the client to retry when the duplicate is still in flight")
    void reportsAnInFlightDuplicate() {
        when(executor.transfer(anyString(), anyString(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value"));
        when(idempotencyService.lookup("key-1", "hash")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transfer("key-1", request))
                .isInstanceOf(TransactionException.class)
                .extracting(e -> ((WalletLedgerException) e).getHttpStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("passes a successful result straight through")
    void passesResultsThrough() {
        TransactionResult expected = new TransactionResult(UUID.randomUUID(), "key-1", "TRANSFER",
                "COMPLETED", new BigDecimal("10.00"), "INR", UUID.randomUUID(), UUID.randomUUID(),
                false, null);
        when(executor.transfer(anyString(), anyString(), any())).thenReturn(expected);

        assertThat(service.transfer("key-1", request)).isSameAs(expected);
        verify(idempotencyService, never()).recordFailure(anyString(), anyString(), any());
    }
}
