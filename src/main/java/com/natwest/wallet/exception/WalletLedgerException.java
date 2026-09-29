package com.natwest.wallet.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for all business exceptions raised by the wallet ledger service.
 * Each subclass carries a stable machine-readable error code and the HTTP status
 * it should be reported with, so the global handler does not need a lookup table.
 */
public class WalletLedgerException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public WalletLedgerException(String message) {
        this(message, "WALLET_ERROR", HttpStatus.BAD_REQUEST, null);
    }

    public WalletLedgerException(String message, String errorCode) {
        this(message, errorCode, HttpStatus.BAD_REQUEST, null);
    }

    public WalletLedgerException(String message, String errorCode, HttpStatus httpStatus) {
        this(message, errorCode, httpStatus, null);
    }

    public WalletLedgerException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
