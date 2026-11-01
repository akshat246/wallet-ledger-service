package com.ledger.wallet.domain;

public enum TransactionType {
    /** Wallet to wallet, inside the system. */
    TRANSFER,
    /** Money entering the system from outside. */
    DEPOSIT,
    /** Money leaving the system. */
    WITHDRAWAL
}
