package com.ledger.wallet.domain;

/**
 * Side of a double-entry ledger row, from the owning wallet's point of view:
 * DEBIT reduces its balance, CREDIT increases it.
 */
public enum EntryType {
    DEBIT,
    CREDIT
}
