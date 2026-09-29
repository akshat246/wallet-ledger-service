package com.natwest.wallet.exception;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientFundsException extends WalletLedgerException {
    public InsufficientFundsException(UUID walletId, BigDecimal available, BigDecimal requested) {
        super(String.format("Insufficient funds in wallet %s: available %s, requested %s",
                            walletId, available, requested),
              "INSUFFICIENT_FUNDS", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
