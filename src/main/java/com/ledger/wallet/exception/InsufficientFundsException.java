package com.ledger.wallet.exception;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientFundsException extends WalletLedgerException {

    public InsufficientFundsException(UUID walletId, BigDecimal available, BigDecimal requested) {
        super("Insufficient funds in wallet %s: available %s, requested %s"
                        .formatted(walletId, available, requested),
                ErrorCode.INSUFFICIENT_FUNDS, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
