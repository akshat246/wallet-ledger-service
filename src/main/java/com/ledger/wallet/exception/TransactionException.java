package com.ledger.wallet.exception;

import org.springframework.http.HttpStatus;

/**
 * A movement failed for a technical reason rather than a business one, such as a duplicate
 * still in flight. Retrying with the same Idempotency-Key is safe.
 */
public class TransactionException extends WalletLedgerException {

    public TransactionException(String message) {
        super(message, ErrorCode.TRANSACTION_FAILED, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public TransactionException(String message, Throwable cause) {
        super(message, ErrorCode.TRANSACTION_FAILED, HttpStatus.SERVICE_UNAVAILABLE, cause);
    }
}
