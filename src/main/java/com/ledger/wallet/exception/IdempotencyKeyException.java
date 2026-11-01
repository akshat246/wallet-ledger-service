package com.ledger.wallet.exception;

import org.springframework.http.HttpStatus;

/** The Idempotency-Key is missing, too long, or reused for a different request. */
public class IdempotencyKeyException extends WalletLedgerException {

    public IdempotencyKeyException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    public IdempotencyKeyException(String message, HttpStatus status) {
        super(message, ErrorCode.IDEMPOTENCY_KEY_ERROR, status);
    }
}
