package com.ledger.wallet.mapper;

import com.ledger.wallet.domain.Transaction;
import com.ledger.wallet.domain.Wallet;
import com.ledger.wallet.dto.response.TransactionResponse;
import com.ledger.wallet.dto.response.TransactionResult;

import java.time.Instant;
import java.util.UUID;

public final class TransactionMapper {

    private TransactionMapper() { }

    public static TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getIdempotencyKey(),
                idOf(transaction.getFromWallet()),
                idOf(transaction.getToWallet()),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getType().name(),
                transaction.getStatus().name(),
                transaction.getFailureReason(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt());
    }

    public static TransactionResult toResult(Transaction transaction) {
        return new TransactionResult(
                transaction.getId(),
                transaction.getIdempotencyKey(),
                transaction.getType().name(),
                transaction.getStatus().name(),
                transaction.getAmount(),
                transaction.getCurrency(),
                idOf(transaction.getFromWallet()),
                idOf(transaction.getToWallet()),
                false,
                transaction.getCreatedAt() != null ? transaction.getCreatedAt() : Instant.now());
    }

    private static UUID idOf(Wallet wallet) {
        return wallet != null ? wallet.getId() : null;
    }
}
