package com.natwest.wallet.exception;

import org.springframework.http.HttpStatus;

public class SameWalletTransferException extends WalletLedgerException {
    public SameWalletTransferException() {
        super("Cannot transfer to the same wallet", "SAME_WALLET_TRANSFER", HttpStatus.BAD_REQUEST);
    }
}
