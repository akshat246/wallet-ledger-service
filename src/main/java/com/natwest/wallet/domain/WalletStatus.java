package com.natwest.wallet.domain;

/**
 * Wallet status enumeration
 */
public enum WalletStatus {
    ACTIVE,     // Wallet is active and can be used
    FROZEN,     // Wallet is temporarily frozen
    CLOSED      // Wallet is closed permanently
}
