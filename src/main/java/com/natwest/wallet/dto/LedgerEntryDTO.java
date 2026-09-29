package com.natwest.wallet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LedgerEntryDTO {
    private UUID id;
    private UUID walletId;
    private UUID transactionId;
    private String entryType;
    private BigDecimal amount;
    private BigDecimal runningBalance;
    private String description;
    private LocalDateTime createdAt;
}
