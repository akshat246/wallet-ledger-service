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
public class WalletDTO {
    private UUID id;
    private UUID userId;
    private BigDecimal balance;
    private String currency;
    private String status;
    private String walletType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
