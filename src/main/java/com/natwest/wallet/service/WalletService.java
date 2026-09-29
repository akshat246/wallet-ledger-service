package com.natwest.wallet.service;

import com.natwest.wallet.domain.LedgerEntry;
import com.natwest.wallet.domain.User;
import com.natwest.wallet.domain.Wallet;
import com.natwest.wallet.domain.WalletStatus;
import com.natwest.wallet.dto.BalanceDTO;
import com.natwest.wallet.dto.CreateWalletRequest;
import com.natwest.wallet.dto.LedgerEntryDTO;
import com.natwest.wallet.dto.WalletDTO;
import com.natwest.wallet.exception.UnsupportedCurrencyException;
import com.natwest.wallet.exception.WalletNotFoundException;
import com.natwest.wallet.repository.LedgerEntryRepository;
import com.natwest.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    /** Mirrors the CHECK constraint on wallets.currency in V1__Initial_Schema.sql. */
    static final Set<String> SUPPORTED_CURRENCIES = Set.of("INR", "USD", "EUR", "GBP");
    private static final String DEFAULT_CURRENCY = "INR";
    private static final String DEFAULT_WALLET_TYPE = "PRIMARY";

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final UserService userService;

    @Transactional
    public WalletDTO createWallet(CreateWalletRequest request) {
        User owner = userService.findUserOrThrow(request.getUserId());

        String currency = request.getCurrency() == null ? DEFAULT_CURRENCY : request.getCurrency().toUpperCase();
        if (!SUPPORTED_CURRENCIES.contains(currency)) {
            throw new UnsupportedCurrencyException(currency, SUPPORTED_CURRENCIES);
        }

        String walletType = (request.getWalletType() == null || request.getWalletType().isBlank())
                ? DEFAULT_WALLET_TYPE
                : request.getWalletType().trim().toUpperCase();

        Wallet wallet = Wallet.builder()
                .user(owner)
                .balance(BigDecimal.ZERO)
                .currency(currency)
                .status(WalletStatus.ACTIVE)
                .walletType(walletType)
                .build();

        wallet = walletRepository.save(wallet);
        log.info("Created wallet {} for user {} ({})", wallet.getId(), owner.getId(), currency);
        return toDto(wallet);
    }

    @Transactional(readOnly = true)
    public WalletDTO getWallet(UUID walletId) {
        return toDto(findWalletOrThrow(walletId));
    }

    @Transactional(readOnly = true)
    public BalanceDTO getBalance(UUID walletId) {
        Wallet wallet = findWalletOrThrow(walletId);
        return BalanceDTO.builder()
                .walletId(wallet.getId())
                .balance(wallet.getBalance())
                .currency(wallet.getCurrency())
                .asOf(LocalDateTime.now())
                .build();
    }

    @Transactional(readOnly = true)
    public List<WalletDTO> getWalletsForUser(UUID userId) {
        userService.findUserOrThrow(userId);
        return walletRepository.findByUserId(userId).stream().map(WalletService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryDTO> getLedger(UUID walletId) {
        findWalletOrThrow(walletId);
        return ledgerEntryRepository.findByWalletIdOrderByCreatedAtDesc(walletId)
                .stream()
                .map(WalletService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Wallet findWalletOrThrow(UUID walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }

    static WalletDTO toDto(Wallet wallet) {
        return WalletDTO.builder()
                .id(wallet.getId())
                .userId(wallet.getUser().getId())
                .balance(wallet.getBalance())
                .currency(wallet.getCurrency())
                .status(wallet.getStatus() != null ? wallet.getStatus().name() : null)
                .walletType(wallet.getWalletType())
                .createdAt(wallet.getCreatedAt())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }

    static LedgerEntryDTO toDto(LedgerEntry entry) {
        return LedgerEntryDTO.builder()
                .id(entry.getId())
                .walletId(entry.getWallet().getId())
                .transactionId(entry.getTransaction().getId())
                .entryType(entry.getEntryType().name())
                .amount(entry.getAmount())
                .runningBalance(entry.getRunningBalance())
                .description(entry.getDescription())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
