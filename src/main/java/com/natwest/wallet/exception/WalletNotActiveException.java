package com.natwest.wallet.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class WalletNotActiveException extends WalletLedgerException {
    public WalletNotActiveException(UUID walletId, String status) {
        super("Wallet " + walletId + " is not active (status: " + status + ")",
              "WALLET_NOT_ACTIVE", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
