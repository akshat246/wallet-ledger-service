package com.natwest.wallet.exception;

import org.springframework.http.HttpStatus;

public class CurrencyMismatchException extends WalletLedgerException {
    public CurrencyMismatchException(String fromCurrency, String toCurrency) {
        super(String.format("Currency mismatch: %s to %s", fromCurrency, toCurrency),
              "CURRENCY_MISMATCH", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
