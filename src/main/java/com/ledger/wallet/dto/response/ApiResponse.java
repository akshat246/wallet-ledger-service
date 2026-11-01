package com.ledger.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/** Envelope for every successful response, so clients parse one shape everywhere. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, String message, Instant timestamp) {

    public static <T> ApiResponse<T> of(T data, String message) {
        return new ApiResponse<>(data, message, Instant.now());
    }

    public static <T> ApiResponse<T> of(T data) {
        return of(data, null);
    }
}
