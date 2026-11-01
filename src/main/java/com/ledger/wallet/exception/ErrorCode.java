package com.ledger.wallet.exception;

/**
 * Stable machine-readable error identifiers. Clients branch on these; the human-readable
 * message beside them may be reworded at any time.
 */
public final class ErrorCode {

    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String MALFORMED_REQUEST = "MALFORMED_REQUEST";
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String USER_ALREADY_EXISTS = "USER_ALREADY_EXISTS";

    public static final String WALLET_NOT_FOUND = "WALLET_NOT_FOUND";
    public static final String WALLET_NOT_ACTIVE = "WALLET_NOT_ACTIVE";
    public static final String UNSUPPORTED_CURRENCY = "UNSUPPORTED_CURRENCY";
    public static final String CURRENCY_MISMATCH = "CURRENCY_MISMATCH";

    public static final String INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";
    public static final String INVALID_AMOUNT = "INVALID_AMOUNT";
    public static final String SAME_WALLET_TRANSFER = "SAME_WALLET_TRANSFER";

    public static final String TRANSACTION_NOT_FOUND = "TRANSACTION_NOT_FOUND";
    public static final String TRANSACTION_FAILED = "TRANSACTION_FAILED";
    public static final String IDEMPOTENCY_KEY_ERROR = "IDEMPOTENCY_KEY_ERROR";
    public static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";
    public static final String DATA_CONFLICT = "DATA_CONFLICT";

    private ErrorCode() { }
}
