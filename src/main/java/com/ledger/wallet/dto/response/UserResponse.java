package com.ledger.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponse(
        UUID id,
        String email,
        String name,
        String phoneNumber,
        String status,
        boolean kycVerified,
        Instant createdAt,
        Instant updatedAt
) { }
