package com.natwest.wallet.controller;

import com.natwest.wallet.dto.BalanceDTO;
import com.natwest.wallet.dto.CreateWalletRequest;
import com.natwest.wallet.dto.LedgerEntryDTO;
import com.natwest.wallet.dto.SuccessResponse;
import com.natwest.wallet.dto.TransactionDTO;
import com.natwest.wallet.dto.WalletDTO;
import com.natwest.wallet.service.TransactionService;
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
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<SuccessResponse<WalletDTO>> createWallet(@Valid @RequestBody CreateWalletRequest request) {
        WalletDTO wallet = walletService.createWallet(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SuccessResponse.ok(wallet, "Wallet created"));
    }

    @GetMapping("/{walletId}")
    public ResponseEntity<SuccessResponse<WalletDTO>> getWallet(@PathVariable("walletId") UUID walletId) {
        return ResponseEntity.ok(SuccessResponse.ok(walletService.getWallet(walletId)));
    }

    @GetMapping("/{walletId}/balance")
    public ResponseEntity<SuccessResponse<BalanceDTO>> getBalance(@PathVariable("walletId") UUID walletId) {
        return ResponseEntity.ok(SuccessResponse.ok(walletService.getBalance(walletId)));
    }

    @GetMapping("/{walletId}/ledger")
    public ResponseEntity<SuccessResponse<List<LedgerEntryDTO>>> getLedger(@PathVariable("walletId") UUID walletId) {
        return ResponseEntity.ok(SuccessResponse.ok(walletService.getLedger(walletId)));
    }

    @GetMapping("/{walletId}/transactions")
    public ResponseEntity<SuccessResponse<List<TransactionDTO>>> getTransactions(@PathVariable("walletId") UUID walletId) {
        return ResponseEntity.ok(SuccessResponse.ok(transactionService.getWalletTransactions(walletId)));
    }
}
