package com.natwest.wallet.exception;

import org.springframework.http.HttpStatus;

import java.util.Set;

public class UnsupportedCurrencyException extends WalletLedgerException {
    public UnsupportedCurrencyException(String currency, Set<String> supported) {
        super("Unsupported currency: " + currency + ". Supported: " + supported,
              "UNSUPPORTED_CURRENCY", HttpStatus.BAD_REQUEST);
    }
}
