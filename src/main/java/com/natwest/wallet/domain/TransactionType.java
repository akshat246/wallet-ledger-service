package com.natwest.wallet.domain;

/**
 * Transaction type enumeration
 */
public enum TransactionType {
    TRANSFER,    // Transfer between two wallets
    DEPOSIT,     // Money deposited into wallet
    WITHDRAWAL   // Money withdrawn from wallet
}
