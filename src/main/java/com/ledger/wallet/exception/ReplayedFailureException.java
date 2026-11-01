package com.ledger.wallet.exception;

import org.springframework.http.HttpStatus;

/**
 * A rejection recorded against an idempotency key earlier, now served again for a retry of
 * the same request.
 *
 * <p>It is a distinct type so the caller knows the rejection is already stored and must not
 * be recorded a second time.
 */
public class ReplayedFailureException extends WalletLedgerException {

    public ReplayedFailureException(String message, String errorCode, HttpStatus status) {
        super(message, errorCode, status);
    }
}
