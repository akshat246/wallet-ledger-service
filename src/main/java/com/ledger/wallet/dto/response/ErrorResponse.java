package com.ledger.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Envelope for every failed response. Clients branch on {@code errorCode}, which is stable;
 * {@code message} is for humans and may be reworded at any time.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String errorCode,
        String message,
        int status,
        String error,
        String path,
        List<FieldError> fieldErrors,
        Instant timestamp
) {

    public record FieldError(String field, String message) { }
}
