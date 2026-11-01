package com.ledger.wallet.domain;

public enum IdempotencyStatus {
    /** Key claimed, outcome not yet known. */
    PENDING,
    /** Request completed; the stored payload is the response to replay. */
    SUCCESS,
    /** Request was rejected; the stored error is replayed on retry. */
    FAILURE
}
