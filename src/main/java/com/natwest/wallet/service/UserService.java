package com.natwest.wallet.service;

import com.natwest.wallet.domain.User;
import com.natwest.wallet.dto.CreateUserRequest;
import com.natwest.wallet.dto.UserDTO;
import com.natwest.wallet.exception.UserAlreadyExistsException;
import com.natwest.wallet.exception.UserNotFoundException;
import com.natwest.wallet.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public UserDTO createUser(CreateUserRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException(email);
        }

        User user = User.builder()
                .email(email)
                .name(request.getName().trim())
                .phoneNumber(request.getPhoneNumber())
                .status(User.UserStatus.ACTIVE)
                .kycVerified(false)
                .build();

        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two requests raced past the existsByEmail check; the unique index wins.
            throw new UserAlreadyExistsException(email);
        }

        log.info("Created user {} ({})", user.getId(), user.getEmail());
        return toDto(user);
    }

    @Transactional(readOnly = true)
    public UserDTO getUser(UUID userId) {
        return toDto(findUserOrThrow(userId));
    }

    @Transactional(readOnly = true)
    public User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    static UserDTO toDto(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .phoneNumber(user.getPhoneNumber())
                .status(user.getStatus() != null ? user.getStatus().name() : null)
                .kycVerified(user.getKycVerified())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
