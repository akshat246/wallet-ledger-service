package com.ledger.wallet.exception;

import org.springframework.http.HttpStatus;

public class SameWalletTransferException extends WalletLedgerException {

    public SameWalletTransferException() {
        super("Source and destination wallet must differ",
                ErrorCode.SAME_WALLET_TRANSFER, HttpStatus.BAD_REQUEST);
    }
}
