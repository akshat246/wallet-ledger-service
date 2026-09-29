package com.natwest.wallet.domain;

/**
 * Transaction status enumeration
 */
public enum TransactionStatus {
    PENDING,    // Transaction initiated but not yet processed
    COMPLETED,  // Transaction successfully completed
    FAILED      // Transaction failed
}
