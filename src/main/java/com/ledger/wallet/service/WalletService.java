package com.ledger.wallet.service;

import com.ledger.wallet.config.WalletProperties;
import com.ledger.wallet.domain.User;
import com.ledger.wallet.domain.Wallet;
import com.ledger.wallet.domain.WalletStatus;
import com.ledger.wallet.dto.request.CreateWalletRequest;
import com.ledger.wallet.dto.response.BalanceResponse;
import com.ledger.wallet.dto.response.LedgerEntryResponse;
import com.ledger.wallet.dto.response.WalletResponse;
import com.ledger.wallet.exception.UnsupportedCurrencyException;
import com.ledger.wallet.exception.WalletNotFoundException;
import com.ledger.wallet.mapper.WalletMapper;
import com.ledger.wallet.repository.LedgerEntryRepository;
import com.ledger.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final UserService userService;
    private final WalletProperties properties;

    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request) {
        User owner = userService.requireUser(request.userId());
        String currency = resolveCurrency(request.currency());

        Wallet wallet = walletRepository.save(Wallet.builder()
                .user(owner)
                .balance(BigDecimal.ZERO)
                .currency(currency)
                .status(WalletStatus.ACTIVE)
                .walletType(resolveType(request.walletType()))
                .build());

        log.info("Created {} wallet {} for user {}", currency, wallet.getId(), owner.getId());
        return WalletMapper.toResponse(wallet);
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID walletId) {
        return WalletMapper.toResponse(requireWallet(walletId));
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(UUID walletId) {
        return WalletMapper.toBalance(requireWallet(walletId));
    }

    @Transactional(readOnly = true)
    public List<WalletResponse> getWalletsOfUser(UUID userId) {
        userService.requireUser(userId);
        return walletRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(WalletMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> getLedger(UUID walletId) {
        requireWallet(walletId);
        return ledgerEntryRepository.findByWalletIdOrderByCreatedAtDesc(walletId).stream()
                .map(WalletMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Wallet requireWallet(UUID walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }

    private String resolveCurrency(String requested) {
        if (requested == null || requested.isBlank()) {
            return properties.defaultCurrency();
        }
        String currency = requested.toUpperCase();
        if (!properties.supportedCurrencies().contains(currency)) {
            throw new UnsupportedCurrencyException(currency, properties.supportedCurrencies());
        }
        return currency;
    }

    private String resolveType(String requested) {
        return (requested == null || requested.isBlank())
                ? properties.defaultType()
                : requested.trim().toUpperCase();
    }
}
