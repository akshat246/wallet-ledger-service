package com.natwest.wallet.exception;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

public class InvalidAmountException extends WalletLedgerException {
    public InvalidAmountException(BigDecimal amount) {
        super("Invalid amount: " + amount + ". Amount must be positive.",
              "INVALID_AMOUNT", HttpStatus.BAD_REQUEST);
    }
}
