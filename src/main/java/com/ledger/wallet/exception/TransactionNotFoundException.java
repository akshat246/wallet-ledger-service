package com.ledger.wallet.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class TransactionNotFoundException extends WalletLedgerException {

    public TransactionNotFoundException(UUID transactionId) {
        super("Transaction not found: " + transactionId,
                ErrorCode.TRANSACTION_NOT_FOUND, HttpStatus.NOT_FOUND);
    }
}
