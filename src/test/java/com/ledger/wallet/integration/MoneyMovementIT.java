package com.ledger.wallet.integration;

import com.ledger.wallet.domain.WalletStatus;
import com.ledger.wallet.dto.request.DepositRequest;
import com.ledger.wallet.dto.request.TransferRequest;
import com.ledger.wallet.dto.request.WithdrawalRequest;
import com.ledger.wallet.dto.response.LedgerEntryResponse;
import com.ledger.wallet.dto.response.TransactionResult;
import com.ledger.wallet.exception.CurrencyMismatchException;
import com.ledger.wallet.exception.InsufficientFundsException;
import com.ledger.wallet.exception.WalletNotActiveException;
import com.ledger.wallet.service.ReconciliationService;
import com.ledger.wallet.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Money movement against the real schema: balances, the ledger behind them, and the
 * guarantee that a rejected movement leaves nothing behind.
 */
class MoneyMovementIT extends AbstractIntegrationTest {

    @Autowired private ReconciliationService reconciliationService;

    @Test
    @DisplayName("a deposit credits the wallet and appends one ledger row")
    void deposit() {
        UUID wallet = newWallet();

        TransactionResult result = transactionService.deposit(key("dep"),
                new DepositRequest(wallet, new BigDecimal("500.00"), "Salary"));

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.replayed()).isFalse();
        assertThat(balanceOf(wallet)).isEqualByComparingTo("500.00");

        List<LedgerEntryResponse> ledger = walletService.getLedger(wallet);
        assertThat(ledger).hasSize(1);
        assertThat(ledger.get(0).entryType()).isEqualTo("CREDIT");
        assertThat(ledger.get(0).runningBalance()).isEqualByComparingTo("500.00");
        assertThat(ledger.get(0).description()).isEqualTo("Salary");
    }

    @Test
    @DisplayName("a transfer moves money and writes both sides of the entry")
    void transfer() {
        UUID from = newFundedWallet("500.00");
        UUID to = newWallet();

        transactionService.transfer(key("txn"),
                new TransferRequest(from, to, new BigDecimal("125.50"), "Rent"));

        assertThat(balanceOf(from)).isEqualByComparingTo("374.50");
        assertThat(balanceOf(to)).isEqualByComparingTo("125.50");

        // Newest first: the debit just made, then the deposit that funded the wallet.
        assertThat(walletService.getLedger(from))
                .extracting(LedgerEntryResponse::entryType)
                .containsExactly("DEBIT", "CREDIT");
        assertThat(walletService.getLedger(to))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.entryType()).isEqualTo("CREDIT");
                    assertThat(entry.runningBalance()).isEqualByComparingTo("125.50");
                });
    }

    @Test
    @DisplayName("a withdrawal debits the wallet")
    void withdrawal() {
        UUID wallet = newFundedWallet("100.00");

        transactionService.withdraw(key("wd"),
                new WithdrawalRequest(wallet, new BigDecimal("40.00"), "ATM"));

        assertThat(balanceOf(wallet)).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("a rejected transfer leaves both balances and the ledger untouched")
    void rejectedTransferChangesNothing() {
        UUID from = newFundedWallet("50.00");
        UUID to = newWallet();
        int ledgerRowsBefore = walletService.getLedger(from).size();

        assertThatThrownBy(() -> transactionService.transfer(key("txn"),
                new TransferRequest(from, to, new BigDecimal("50.01"), null)))
                .isInstanceOf(InsufficientFundsException.class);

        // The whole unit of work rolled back: no partial debit, no orphaned ledger row, and
        // no transaction record left behind in PENDING.
        assertThat(balanceOf(from)).isEqualByComparingTo("50.00");
        assertThat(balanceOf(to)).isEqualByComparingTo("0.00");
        assertThat(walletService.getLedger(from)).hasSize(ledgerRowsBefore);
        assertThat(transactionService.getWalletTransactions(to)).isEmpty();
    }

    @Test
    @DisplayName("a frozen wallet cannot send money")
    void frozenWalletIsRejected() {
        UUID from = newFundedWallet("100.00");
        UUID to = newWallet();
        setWalletStatus(from, WalletStatus.FROZEN);

        assertThatThrownBy(() -> transactionService.transfer(key("txn"),
                new TransferRequest(from, to, new BigDecimal("10.00"), null)))
                .isInstanceOf(WalletNotActiveException.class);

        assertThat(balanceOf(from)).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("money cannot cross currencies")
    void currencyMismatchIsRejected() {
        UUID rupees = newWallet("INR");
        UUID dollars = newWallet("USD");
        fund(rupees, "100.00");

        assertThatThrownBy(() -> transactionService.transfer(key("txn"),
                new TransferRequest(rupees, dollars, new BigDecimal("10.00"), null)))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    @DisplayName("every balance still equals its ledger after a series of movements")
    void balancesReconcileWithTheLedger() {
        UUID a = newFundedWallet("1000.00");
        UUID b = newFundedWallet("250.00");

        transactionService.transfer(key("t1"), new TransferRequest(a, b, new BigDecimal("300.00"), null));
        transactionService.transfer(key("t2"), new TransferRequest(b, a, new BigDecimal("125.25"), null));
        transactionService.withdraw(key("t3"), new WithdrawalRequest(a, new BigDecimal("75.75"), null));
        transactionService.deposit(key("t4"), new DepositRequest(b, new BigDecimal("10.00"), null));

        // This is what the double-entry design exists for: the materialised balance is only
        // an optimisation, and the ledger is the authority on what it should be.
        assertThat(reconciliationService.reconcile(a).balanced()).isTrue();
        assertThat(reconciliationService.reconcile(b).balanced()).isTrue();

        assertThat(balanceOf(a)).isEqualByComparingTo("749.50");
        assertThat(balanceOf(b)).isEqualByComparingTo("434.75");

        // Nothing created or destroyed beyond the deposit and the withdrawal.
        assertThat(balanceOf(a).add(balanceOf(b))).isEqualByComparingTo("1184.25");
    }

    @Test
    @DisplayName("a wallet's history includes money it sent and money it received")
    void transactionHistory() {
        UUID a = newFundedWallet("100.00");
        UUID b = newFundedWallet("100.00");

        transactionService.transfer(key("t1"), new TransferRequest(a, b, new BigDecimal("10.00"), null));
        transactionService.transfer(key("t2"), new TransferRequest(b, a, new BigDecimal("5.00"), null));

        // One funding deposit plus both transfers, for each wallet.
        assertThat(transactionService.getWalletTransactions(a)).hasSize(3);
        assertThat(transactionService.getWalletTransactions(b)).hasSize(3);
    }
}
