package com.ledger.wallet.mapper;

import com.ledger.wallet.domain.User;
import com.ledger.wallet.dto.response.UserResponse;

public final class UserMapper {

    private UserMapper() { }

    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getPhoneNumber(),
                user.getStatus().name(),
                Boolean.TRUE.equals(user.getKycVerified()),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
