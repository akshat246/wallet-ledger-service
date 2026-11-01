package com.ledger.wallet.service;

import com.ledger.wallet.domain.EntryType;
import com.ledger.wallet.domain.Wallet;
import com.ledger.wallet.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Proves the core accounting invariant: a wallet's stored balance equals its ledger credits
 * minus its debits. Keeping the balance as a column makes reads cheap, and this check is
 * what stops that optimisation from quietly drifting away from the truth.
 *
 * <p>A discrepancy means a bug let a balance change without a matching ledger row. That is
 * worth alerting on, not correcting automatically.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final WalletService walletService;

    @Transactional(readOnly = true)
    public Reconciliation reconcile(UUID walletId) {
        Wallet wallet = walletService.requireWallet(walletId);
        BigDecimal credits = ledgerEntryRepository.sumAmount(walletId, EntryType.CREDIT);
        BigDecimal debits = ledgerEntryRepository.sumAmount(walletId, EntryType.DEBIT);

        Reconciliation result = new Reconciliation(walletId, wallet.getBalance(), credits.subtract(debits));
        if (!result.balanced()) {
            log.error("Ledger mismatch on wallet {}: balance {} but ledger says {}",
                    walletId, result.storedBalance(), result.ledgerBalance());
        }
        return result;
    }

    public record Reconciliation(UUID walletId, BigDecimal storedBalance, BigDecimal ledgerBalance) {

        public boolean balanced() {
            return storedBalance.compareTo(ledgerBalance) == 0;
        }

        public BigDecimal discrepancy() {
            return storedBalance.subtract(ledgerBalance);
        }
    }
}
