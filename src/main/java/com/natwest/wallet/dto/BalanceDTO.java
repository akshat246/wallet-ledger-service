package com.natwest.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lightweight balance view returned by GET /wallets/{id}/balance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceDTO {
    private UUID walletId;
    private BigDecimal balance;
    private String currency;
    private LocalDateTime asOf;
}
