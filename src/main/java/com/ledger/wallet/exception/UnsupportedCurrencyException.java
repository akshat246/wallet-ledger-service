package com.ledger.wallet.exception;

import org.springframework.http.HttpStatus;

import java.util.Collection;

public class UnsupportedCurrencyException extends WalletLedgerException {

    public UnsupportedCurrencyException(String currency, Collection<String> supported) {
        super("Unsupported currency %s; supported: %s".formatted(currency, supported),
                ErrorCode.UNSUPPORTED_CURRENCY, HttpStatus.BAD_REQUEST);
    }
}
