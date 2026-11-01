package com.ledger.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WalletResponse(
        UUID id,
        UUID userId,
        BigDecimal balance,
        String currency,
        String status,
        String walletType,
        Instant createdAt,
        Instant updatedAt
) { }
