package com.ledger.wallet.exception;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

public class InvalidAmountException extends WalletLedgerException {

    public InvalidAmountException(BigDecimal amount) {
        super("Amount must be positive, was " + amount,
                ErrorCode.INVALID_AMOUNT, HttpStatus.BAD_REQUEST);
    }
}
