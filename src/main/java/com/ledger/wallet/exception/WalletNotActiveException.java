package com.ledger.wallet.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * The request is well formed but the wallet is frozen or closed, so the rule says no:
 * 422 rather than 400.
 */
public class WalletNotActiveException extends WalletLedgerException {

    public WalletNotActiveException(UUID walletId, String status) {
        super("Wallet " + walletId + " is not active (status: " + status + ")",
                ErrorCode.WALLET_NOT_ACTIVE, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
