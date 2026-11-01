package com.ledger.wallet.controller;

import com.ledger.wallet.dto.request.CreateWalletRequest;
import com.ledger.wallet.dto.response.ApiResponse;
import com.ledger.wallet.dto.response.BalanceResponse;
import com.ledger.wallet.dto.response.LedgerEntryResponse;
import com.ledger.wallet.dto.response.TransactionResponse;
import com.ledger.wallet.dto.response.WalletResponse;
import com.ledger.wallet.service.TransactionService;
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
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<ApiResponse<WalletResponse>> create(@Valid @RequestBody CreateWalletRequest request,
                                                              UriComponentsBuilder uriBuilder) {
        WalletResponse wallet = walletService.createWallet(request);
        return ResponseEntity
                .created(uriBuilder.path("/api/v1/wallets/{id}").build(wallet.id()))
                .body(ApiResponse.of(wallet, "Wallet created"));
    }

    @GetMapping("/{walletId}")
    public ApiResponse<WalletResponse> get(@PathVariable("walletId") UUID walletId) {
        return ApiResponse.of(walletService.getWallet(walletId));
    }

    @GetMapping("/{walletId}/balance")
    public ApiResponse<BalanceResponse> balance(@PathVariable("walletId") UUID walletId) {
        return ApiResponse.of(walletService.getBalance(walletId));
    }

    /** The double-entry rows for this wallet, newest first. */
    @GetMapping("/{walletId}/ledger")
    public ApiResponse<List<LedgerEntryResponse>> ledger(@PathVariable("walletId") UUID walletId) {
        return ApiResponse.of(walletService.getLedger(walletId));
    }

    /** Transactions this wallet took part in, as sender or receiver. */
    @GetMapping("/{walletId}/transactions")
    public ApiResponse<List<TransactionResponse>> transactions(@PathVariable("walletId") UUID walletId) {
        return ApiResponse.of(transactionService.getWalletTransactions(walletId));
    }
}
