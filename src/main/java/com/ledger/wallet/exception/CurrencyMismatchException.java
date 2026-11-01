package com.ledger.wallet.exception;

import org.springframework.http.HttpStatus;

/**
 * Transfers are same-currency only. Crossing currencies would need an exchange rate, a
 * spread, and a record of both: a different feature, not a silent conversion.
 */
public class CurrencyMismatchException extends WalletLedgerException {

    public CurrencyMismatchException(String from, String to) {
        super("Cannot transfer between %s and %s wallets".formatted(from, to),
                ErrorCode.CURRENCY_MISMATCH, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
