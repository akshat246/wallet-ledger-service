package com.natwest.wallet.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when an Idempotency-Key is missing, malformed, or reused with a different payload.
 */
public class IdempotencyKeyException extends WalletLedgerException {
    public IdempotencyKeyException(String message) {
        super(message, "IDEMPOTENCY_KEY_ERROR", HttpStatus.BAD_REQUEST);
    }

    public IdempotencyKeyException(String message, HttpStatus status) {
        super(message, "IDEMPOTENCY_KEY_ERROR", status);
    }
}
