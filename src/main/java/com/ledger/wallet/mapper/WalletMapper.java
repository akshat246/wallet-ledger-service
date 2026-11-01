package com.ledger.wallet.mapper;

import com.ledger.wallet.domain.LedgerEntry;
import com.ledger.wallet.domain.Wallet;
import com.ledger.wallet.dto.response.BalanceResponse;
import com.ledger.wallet.dto.response.LedgerEntryResponse;
import com.ledger.wallet.dto.response.WalletResponse;

import java.time.Instant;

public final class WalletMapper {

    private WalletMapper() { }

    public static WalletResponse toResponse(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                // getId() on a lazy proxy is answered by the proxy itself and issues no query.
                wallet.getUser().getId(),
                wallet.getBalance(),
                wallet.getCurrency(),
                wallet.getStatus().name(),
                wallet.getWalletType(),
                wallet.getCreatedAt(),
                wallet.getUpdatedAt());
    }

    public static BalanceResponse toBalance(Wallet wallet) {
        return new BalanceResponse(wallet.getId(), wallet.getBalance(), wallet.getCurrency(), Instant.now());
    }

    public static LedgerEntryResponse toResponse(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getWallet().getId(),
                entry.getTransaction().getId(),
                entry.getEntryType().name(),
                entry.getAmount(),
                entry.getRunningBalance(),
                entry.getDescription(),
                entry.getCreatedAt());
    }
}
