package com.natwest.wallet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDTO {
    private UUID id;
    private String email;
    private String name;
    private String phoneNumber;
    private String status;
    private Boolean kycVerified;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
