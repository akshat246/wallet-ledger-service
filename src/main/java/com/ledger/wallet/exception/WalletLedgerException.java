package com.ledger.wallet.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base type for every business failure in the service.
 *
 * <p>Each subclass carries both a stable error code and the HTTP status it should be
 * reported with, so the web layer translates any of them without a lookup table and adding a
 * new failure never means editing the handler.
 *
 * <p>It extends {@link RuntimeException} deliberately: Spring rolls a transaction back on
 * unchecked exceptions by default, so every rejection undoes its own writes.
 */
@Getter
public class WalletLedgerException extends RuntimeException {

    private final String errorCode;
    private final transient HttpStatus httpStatus;

    protected WalletLedgerException(String message, String errorCode, HttpStatus httpStatus) {
        this(message, errorCode, httpStatus, null);
    }

    protected WalletLedgerException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
