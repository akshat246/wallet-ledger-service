package com.ledger.wallet.service;

import com.ledger.wallet.domain.User;
import com.ledger.wallet.domain.UserStatus;
import com.ledger.wallet.dto.request.CreateUserRequest;
import com.ledger.wallet.dto.response.UserResponse;
import com.ledger.wallet.exception.UserAlreadyExistsException;
import com.ledger.wallet.exception.UserNotFoundException;
import com.ledger.wallet.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private UserService userService;

    @Test
    @DisplayName("normalises the email so Alice@EXAMPLE.com and alice@example.com are one account")
    void normalisesEmail() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.createUser(
                new CreateUserRequest("  Alice@EXAMPLE.com  ", "  Alice  ", null));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(captor.getValue().getName()).isEqualTo("Alice");
        assertThat(captor.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.kycVerified()).isFalse();
    }

    @Test
    @DisplayName("rejects an email that already exists")
    void rejectsDuplicateEmail() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(
                new CreateUserRequest("alice@example.com", "Alice", null)))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    @DisplayName("turns a unique-index violation into the same duplicate error")
    void translatesConstraintViolations() {
        // Two requests can both pass existsByEmail; only the index settles it.
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("users_email_key"));

        assertThatThrownBy(() -> userService.createUser(
                new CreateUserRequest("alice@example.com", "Alice", null)))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    @DisplayName("stores a blank phone number as null rather than an empty string")
    void blankPhoneBecomesNull() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(i -> i.getArgument(0));

        userService.createUser(new CreateUserRequest("a@example.com", "Alice", "   "));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPhoneNumber()).isNull();
    }

    @Test
    @DisplayName("reports an unknown user as not found")
    void unknownUser() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(id)).isInstanceOf(UserNotFoundException.class);
    }
}
