package com.ledger.wallet.service;

import com.ledger.wallet.config.WalletProperties;
import com.ledger.wallet.domain.User;
import com.ledger.wallet.domain.Wallet;
import com.ledger.wallet.domain.WalletStatus;
import com.ledger.wallet.dto.request.CreateWalletRequest;
import com.ledger.wallet.dto.response.WalletResponse;
import com.ledger.wallet.exception.UnsupportedCurrencyException;
import com.ledger.wallet.exception.WalletNotFoundException;
import com.ledger.wallet.repository.LedgerEntryRepository;
import com.ledger.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private UserService userService;

    private WalletService walletService;

    private final User owner = User.builder().id(UUID.randomUUID()).email("a@example.com").build();

    @BeforeEach
    void setUp() {
        walletService = new WalletService(walletRepository, ledgerEntryRepository, userService,
                new WalletProperties("INR", Set.of("INR", "USD", "EUR", "GBP"), "PRIMARY"));
    }

    @Test
    @DisplayName("opens a wallet with a zero balance")
    void opensEmptyWallet() {
        when(userService.requireUser(owner.getId())).thenReturn(owner);
        when(walletRepository.save(any(Wallet.class))).thenAnswer(i -> i.getArgument(0));

        WalletResponse response = walletService.createWallet(
                new CreateWalletRequest(owner.getId(), "USD", null));

        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.status()).isEqualTo(WalletStatus.ACTIVE.name());
        assertThat(response.walletType()).isEqualTo("PRIMARY");
    }

    @Test
    @DisplayName("falls back to the configured default currency")
    void defaultsCurrency() {
        when(userService.requireUser(owner.getId())).thenReturn(owner);
        when(walletRepository.save(any(Wallet.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(walletService.createWallet(new CreateWalletRequest(owner.getId(), null, null))
                .currency()).isEqualTo("INR");
    }

    @Test
    @DisplayName("accepts a lower-case currency code")
    void normalisesCurrencyCase() {
        when(userService.requireUser(owner.getId())).thenReturn(owner);
        when(walletRepository.save(any(Wallet.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(walletService.createWallet(new CreateWalletRequest(owner.getId(), "eur", null))
                .currency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("refuses a currency the service does not support")
    void rejectsUnsupportedCurrency() {
        when(userService.requireUser(owner.getId())).thenReturn(owner);

        assertThatThrownBy(() -> walletService.createWallet(
                new CreateWalletRequest(owner.getId(), "JPY", null)))
                .isInstanceOf(UnsupportedCurrencyException.class)
                .hasMessageContaining("JPY");
    }

    @Test
    @DisplayName("reports an unknown wallet as not found")
    void unknownWallet() {
        UUID id = UUID.randomUUID();
        when(walletRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getBalance(id))
                .isInstanceOf(WalletNotFoundException.class);
    }
}
