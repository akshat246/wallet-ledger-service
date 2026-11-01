package com.ledger.wallet.integration;

import com.ledger.wallet.dto.request.DepositRequest;
import com.ledger.wallet.dto.request.TransferRequest;
import com.ledger.wallet.dto.response.TransactionResult;
import com.ledger.wallet.exception.IdempotencyKeyException;
import com.ledger.wallet.exception.InsufficientFundsException;
import com.ledger.wallet.exception.WalletLedgerException;
import com.ledger.wallet.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Retry safety. A client that never saw a response must be able to send the same request
 * again without the money moving twice, and must get the answer it would have got.
 */
class IdempotencyIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("repeating a transfer with the same key moves money once")
    void repeatedTransferMovesMoneyOnce() {
        UUID from = newFundedWallet("500.00");
        UUID to = newWallet();
        String key = key("retry");
        TransferRequest request = new TransferRequest(from, to, new BigDecimal("100.00"), "Rent");

        TransactionResult first = transactionService.transfer(key, request);
        TransactionResult second = transactionService.transfer(key, request);

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.transactionId()).isEqualTo(first.transactionId());

        assertThat(balanceOf(from)).isEqualByComparingTo("400.00");
        assertThat(balanceOf(to)).isEqualByComparingTo("100.00");
        // One ledger row on the receiving side, not two.
        assertThat(walletService.getLedger(to)).hasSize(1);
    }

    @Test
    @DisplayName("a replay returns the stored outcome even if a cosmetic field differed")
    void replayReturnsTheStoredOutcome() {
        UUID wallet = newWallet();
        String key = key("dep");

        TransactionResult first = transactionService.deposit(key,
                new DepositRequest(wallet, new BigDecimal("75.25"), "First"));
        TransactionResult replay = transactionService.deposit(key,
                new DepositRequest(wallet, new BigDecimal("75.25"), "A different description"));

        // The fingerprint covers the business fields, not the description.
        assertThat(replay.transactionId()).isEqualTo(first.transactionId());
        assertThat(replay.amount()).isEqualByComparingTo("75.25");
        assertThat(balanceOf(wallet)).isEqualByComparingTo("75.25");
    }

    @Test
    @DisplayName("reusing a key for a different amount is refused rather than replayed")
    void refusesKeyReuse() {
        UUID from = newFundedWallet("500.00");
        UUID to = newWallet();
        String key = key("reuse");

        transactionService.transfer(key, new TransferRequest(from, to, new BigDecimal("100.00"), null));

        assertThatThrownBy(() -> transactionService.transfer(key,
                new TransferRequest(from, to, new BigDecimal("250.00"), null)))
                .isInstanceOf(IdempotencyKeyException.class);

        // The second request did nothing at all.
        assertThat(balanceOf(from)).isEqualByComparingTo("400.00");
    }

    @Test
    @DisplayName("retrying a rejected request gets the same rejection, not a late success")
    void rejectionsAreReplayedToo() {
        UUID from = newFundedWallet("50.00");
        UUID to = newWallet();
        String key = key("poor");
        TransferRequest request = new TransferRequest(from, to, new BigDecimal("100.00"), null);

        assertThatThrownBy(() -> transactionService.transfer(key, request))
                .isInstanceOf(InsufficientFundsException.class);

        // Money arrives between the first attempt and the retry.
        fund(from, "500.00");

        // The retry is the same request, so it must still be refused. Letting it succeed now
        // would mean one request produced two different outcomes.
        assertThatThrownBy(() -> transactionService.transfer(key, request))
                .isInstanceOf(WalletLedgerException.class)
                .hasMessageContaining("Insufficient funds");

        assertThat(balanceOf(to)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("a fresh key after a rejection still works")
    void aNewKeySucceedsAfterARejection() {
        UUID from = newFundedWallet("50.00");
        UUID to = newWallet();

        assertThatThrownBy(() -> transactionService.transfer(key("first"),
                new TransferRequest(from, to, new BigDecimal("100.00"), null)))
                .isInstanceOf(InsufficientFundsException.class);

        fund(from, "100.00");
        transactionService.transfer(key("second"),
                new TransferRequest(from, to, new BigDecimal("100.00"), null));

        assertThat(balanceOf(to)).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("different keys for the same payload move money twice, as asked")
    void differentKeysAreDifferentRequests() {
        UUID wallet = newWallet();

        transactionService.deposit(key("a"), new DepositRequest(wallet, new BigDecimal("10.00"), null));
        transactionService.deposit(key("b"), new DepositRequest(wallet, new BigDecimal("10.00"), null));

        // Idempotency is keyed on the header, not the payload: two genuine deposits of the
        // same amount must both land.
        assertThat(balanceOf(wallet)).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("a rejection is stored as FAILURE and a success as SUCCESS")
    void outcomesAreRecordedAgainstTheKey() {
        UUID from = newFundedWallet("10.00");
        UUID to = newWallet();
        String goodKey = key("ok");
        String badKey = key("bad");

        transactionService.transfer(goodKey, new TransferRequest(from, to, new BigDecimal("5.00"), null));
        assertThatThrownBy(() -> transactionService.transfer(badKey,
                new TransferRequest(from, to, new BigDecimal("500.00"), null)))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(statusOfKey(goodKey)).isEqualTo("SUCCESS");
        assertThat(statusOfKey(badKey)).isEqualTo("FAILURE");
    }

    private String statusOfKey(String key) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM idempotency_keys WHERE idempotency_key = ?", String.class, key);
    }
}
