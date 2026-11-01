package com.ledger.wallet.service;

import com.ledger.wallet.domain.User;
import com.ledger.wallet.domain.UserStatus;
import com.ledger.wallet.dto.request.CreateUserRequest;
import com.ledger.wallet.dto.response.UserResponse;
import com.ledger.wallet.exception.UserAlreadyExistsException;
import com.ledger.wallet.exception.UserNotFoundException;
import com.ledger.wallet.mapper.UserMapper;
import com.ledger.wallet.repository.UserRepository;
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
    public UserResponse createUser(CreateUserRequest request) {
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException(email);
        }

        User user = User.builder()
                .email(email)
                .name(request.name().trim())
                .phoneNumber(blankToNull(request.phoneNumber()))
                .status(UserStatus.ACTIVE)
                .kycVerified(false)
                .build();

        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Another request inserted the same email between the check above and this write.
            // The unique index is the real guarantee; the check only gives a nicer error.
            throw new UserAlreadyExistsException(email);
        }

        log.info("Created user {}", user.getId());
        return UserMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID userId) {
        return UserMapper.toResponse(requireUser(userId));
    }

    @Transactional(readOnly = true)
    public User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
