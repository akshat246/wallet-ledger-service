package com.ledger.wallet.domain;

public enum WalletStatus {
    /** Normal operation: can send and receive. */
    ACTIVE,
    /** Temporarily blocked, e.g. during a fraud review. */
    FROZEN,
    /** Permanently closed; no further movement allowed. */
    CLOSED
}
