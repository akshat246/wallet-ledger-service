package com.natwest.wallet.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class WalletNotFoundException extends WalletLedgerException {
    public WalletNotFoundException(UUID walletId) {
        super("Wallet not found: " + walletId, "WALLET_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}
