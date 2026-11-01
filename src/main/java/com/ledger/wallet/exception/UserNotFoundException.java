package com.ledger.wallet.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class UserNotFoundException extends WalletLedgerException {

    public UserNotFoundException(UUID userId) {
        super("User not found: " + userId, ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND);
    }
}
