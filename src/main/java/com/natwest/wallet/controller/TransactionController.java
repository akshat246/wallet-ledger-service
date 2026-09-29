package com.natwest.wallet.controller;

import com.natwest.wallet.dto.DepositRequest;
import com.natwest.wallet.dto.SuccessResponse;
import com.natwest.wallet.dto.TransactionDTO;
import com.natwest.wallet.dto.TransactionResponse;
import com.natwest.wallet.dto.TransferRequest;
import com.natwest.wallet.dto.WithdrawalRequest;
import com.natwest.wallet.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Money movement. Every POST requires an {@code Idempotency-Key} header; sending the same
 * key again returns the original result (HTTP 200 with {@code replayed: true}) instead of
 * moving money twice.
 */
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final TransactionService transactionService;

    @PostMapping("/transfer")
    public ResponseEntity<SuccessResponse<TransactionResponse>> transfer(
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return respond(transactionService.transfer(idempotencyKey, request), "Transfer completed");
    }

    @PostMapping("/deposit")
    public ResponseEntity<SuccessResponse<TransactionResponse>> deposit(
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @Valid @RequestBody DepositRequest request) {
        return respond(transactionService.deposit(idempotencyKey, request), "Deposit completed");
    }

    @PostMapping("/withdraw")
    public ResponseEntity<SuccessResponse<TransactionResponse>> withdraw(
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @Valid @RequestBody WithdrawalRequest request) {
        return respond(transactionService.withdraw(idempotencyKey, request), "Withdrawal completed");
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<SuccessResponse<TransactionDTO>> getTransaction(@PathVariable("transactionId") UUID transactionId) {
        return ResponseEntity.ok(SuccessResponse.ok(transactionService.getTransaction(transactionId)));
    }

    private static ResponseEntity<SuccessResponse<TransactionResponse>> respond(TransactionResponse result, String message) {
        HttpStatus status = result.isReplayed() ? HttpStatus.OK : HttpStatus.CREATED;
        String text = result.isReplayed() ? "Duplicate request; returning original result" : message;
        return ResponseEntity.status(status).body(SuccessResponse.ok(result, text));
    }
}
