package com.ledger.wallet.controller;

import com.ledger.wallet.dto.request.CreateUserRequest;
import com.ledger.wallet.dto.response.ApiResponse;
import com.ledger.wallet.dto.response.UserResponse;
import com.ledger.wallet.dto.response.WalletResponse;
import com.ledger.wallet.service.UserService;
import com.ledger.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final WalletService walletService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody CreateUserRequest request,
                                                            UriComponentsBuilder uriBuilder) {
        UserResponse user = userService.createUser(request);
        return ResponseEntity
                .created(uriBuilder.path("/api/v1/users/{id}").build(user.id()))
                .body(ApiResponse.of(user, "User created"));
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserResponse> get(@PathVariable("userId") UUID userId) {
        return ApiResponse.of(userService.getUser(userId));
    }

    @GetMapping("/{userId}/wallets")
    public ApiResponse<List<WalletResponse>> wallets(@PathVariable("userId") UUID userId) {
        return ApiResponse.of(walletService.getWalletsOfUser(userId));
    }
}
