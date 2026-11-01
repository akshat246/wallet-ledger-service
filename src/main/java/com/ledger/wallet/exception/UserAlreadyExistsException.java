package com.ledger.wallet.exception;

import org.springframework.http.HttpStatus;

public class UserAlreadyExistsException extends WalletLedgerException {

    public UserAlreadyExistsException(String email) {
        super("A user with email " + email + " already exists",
                ErrorCode.USER_ALREADY_EXISTS, HttpStatus.CONFLICT);
    }
}
