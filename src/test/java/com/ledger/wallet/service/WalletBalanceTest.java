package com.ledger.wallet.service;

import com.ledger.wallet.domain.Wallet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The balance rules live on the entity, so no service can move money without them. */
class WalletBalanceTest {

    private static Wallet walletWith(String balance) {
        return Wallet.builder().balance(new BigDecimal(balance)).currency("INR").build();
    }

    @Test
    @DisplayName("adds and subtracts exactly, with no floating point drift")
    void exactDecimalArithmetic() {
        Wallet wallet = walletWith("0.00");

        wallet.credit(new BigDecimal("0.10"));
        wallet.credit(new BigDecimal("0.20"));

        // The reason balances are BigDecimal: 0.1 + 0.2 as a double is 0.30000000000000004.
        assertThat(wallet.getBalance()).isEqualByComparingTo("0.30");
    }

    @Test
    @DisplayName("allows a debit of the entire balance")
    void debitToZero() {
        Wallet wallet = walletWith("25.50");

        wallet.debit(new BigDecimal("25.50"));

        assertThat(wallet.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("refuses a debit larger than the balance")
    void cannotOverdraw() {
        Wallet wallet = walletWith("25.50");

        assertThatThrownBy(() -> wallet.debit(new BigDecimal("25.51")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(wallet.getBalance()).isEqualByComparingTo("25.50");
    }

    @Test
    @DisplayName("refuses zero and negative amounts on both sides")
    void requiresPositiveAmounts() {
        Wallet wallet = walletWith("10.00");

        assertThatThrownBy(() -> wallet.credit(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> wallet.credit(new BigDecimal("-1.00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> wallet.debit(new BigDecimal("-1.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
