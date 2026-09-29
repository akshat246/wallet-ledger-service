package com.natwest.wallet.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a transaction cannot be completed for a technical (not business) reason,
 * e.g. a lock could not be acquired in time. Retryable.
 */
public class TransactionException extends WalletLedgerException {
    public TransactionException(String message) {
        super(message, "TRANSACTION_FAILED", HttpStatus.SERVICE_UNAVAILABLE);
    }

    public TransactionException(String message, Throwable cause) {
        super(message, "TRANSACTION_FAILED", HttpStatus.SERVICE_UNAVAILABLE, cause);
    }
}
