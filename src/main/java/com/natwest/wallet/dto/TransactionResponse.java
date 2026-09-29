package com.natwest.wallet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response body for the money-movement endpoints (transfer / deposit / withdraw).
 * {@code replayed} is true when the request was recognised as a retry and the
 * original result was returned instead of re-processing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionResponse {
    private UUID transactionId;
    private String idempotencyKey;
    private String type;
    private String status;
    private BigDecimal amount;
    private String currency;
    private UUID fromWalletId;
    private UUID toWalletId;
    private String failureReason;
    private boolean replayed;
    private LocalDateTime timestamp;
}
