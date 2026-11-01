package com.ledger.wallet.controller;

import com.ledger.wallet.dto.request.DepositRequest;
import com.ledger.wallet.dto.request.TransferRequest;
import com.ledger.wallet.dto.request.WithdrawalRequest;
import com.ledger.wallet.dto.response.ApiResponse;
import com.ledger.wallet.dto.response.TransactionResponse;
import com.ledger.wallet.dto.response.TransactionResult;
import com.ledger.wallet.service.TransactionService;
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
 * Money movement.
 *
 * <p>Every POST here requires an {@code Idempotency-Key}. Sending the same key again returns
 * the original outcome, 200 with {@code replayed: true}, rather than moving money twice,
 * which is what makes it safe for a client to retry when it never saw the first response.
 */
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final TransactionService transactionService;

    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<TransactionResult>> transfer(
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return respond(transactionService.transfer(idempotencyKey, request), "Transfer completed");
    }

    @PostMapping("/deposit")
    public ResponseEntity<ApiResponse<TransactionResult>> deposit(
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @Valid @RequestBody DepositRequest request) {
        return respond(transactionService.deposit(idempotencyKey, request), "Deposit completed");
    }

    @PostMapping("/withdraw")
    public ResponseEntity<ApiResponse<TransactionResult>> withdraw(
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @Valid @RequestBody WithdrawalRequest request) {
        return respond(transactionService.withdraw(idempotencyKey, request), "Withdrawal completed");
    }

    @GetMapping("/{transactionId}")
    public ApiResponse<TransactionResponse> get(@PathVariable("transactionId") UUID transactionId) {
        return ApiResponse.of(transactionService.getTransaction(transactionId));
    }

    /**
     * 201 when this call performed the movement, 200 when it only replayed an earlier one:
     * a replay creates nothing, so it is not a Created.
     */
    private static ResponseEntity<ApiResponse<TransactionResult>> respond(TransactionResult result, String message) {
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        String text = result.replayed() ? "Duplicate request; original result returned" : message;
        return ResponseEntity.status(status).body(ApiResponse.of(result, text));
    }
}
