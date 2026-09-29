package com.natwest.wallet.controller;

import com.natwest.wallet.dto.CreateUserRequest;
import com.natwest.wallet.dto.SuccessResponse;
import com.natwest.wallet.dto.UserDTO;
import com.natwest.wallet.dto.WalletDTO;
import com.natwest.wallet.service.UserService;
import com.natwest.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final WalletService walletService;

    @PostMapping
    public ResponseEntity<SuccessResponse<UserDTO>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserDTO user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SuccessResponse.ok(user, "User created"));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<SuccessResponse<UserDTO>> getUser(@PathVariable("userId") UUID userId) {
        return ResponseEntity.ok(SuccessResponse.ok(userService.getUser(userId)));
    }

    @GetMapping("/{userId}/wallets")
    public ResponseEntity<SuccessResponse<List<WalletDTO>>> getUserWallets(@PathVariable("userId") UUID userId) {
        return ResponseEntity.ok(SuccessResponse.ok(walletService.getWalletsForUser(userId)));
    }
}
