package com.natwest.wallet.domain;

/**
 * Ledger entry type enumeration
 * Used for double-entry bookkeeping
 */
public enum EntryType {
    DEBIT,      // Money going out of wallet
    CREDIT      // Money coming into wallet
}
