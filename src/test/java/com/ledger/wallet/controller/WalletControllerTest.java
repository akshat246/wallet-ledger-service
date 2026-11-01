package com.ledger.wallet.controller;

import com.ledger.wallet.dto.response.BalanceResponse;
import com.ledger.wallet.dto.response.WalletResponse;
import com.ledger.wallet.exception.UnsupportedCurrencyException;
import com.ledger.wallet.exception.WalletNotFoundException;
import com.ledger.wallet.service.TransactionService;
import com.ledger.wallet.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WalletController.class)
class WalletControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private WalletService walletService;
    @MockBean private TransactionService transactionService;

    private static final UUID WALLET_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    @DisplayName("creating a wallet returns 201 with a Location header")
    void createsWallet() throws Exception {
        when(walletService.createWallet(any())).thenReturn(new WalletResponse(
                WALLET_ID, USER_ID, BigDecimal.ZERO, "INR", "ACTIVE", "PRIMARY",
                Instant.now(), Instant.now()));

        mockMvc.perform(post("/api/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + USER_ID + "\",\"currency\":\"INR\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/wallets/" + WALLET_ID)))
                .andExpect(jsonPath("$.data.balance").value(0));
    }

    @Test
    @DisplayName("a missing userId fails validation")
    void requiresUserId() throws Exception {
        mockMvc.perform(post("/api/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"INR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("userId"));
    }

    @Test
    @DisplayName("a malformed currency fails validation before reaching the service")
    void rejectsBadCurrencyFormat() throws Exception {
        mockMvc.perform(post("/api/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + USER_ID + "\",\"currency\":\"RUPEES\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("a well-formed but unsupported currency is reported by the service")
    void rejectsUnsupportedCurrency() throws Exception {
        when(walletService.createWallet(any()))
                .thenThrow(new UnsupportedCurrencyException("JPY", Set.of("INR", "USD")));

        mockMvc.perform(post("/api/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + USER_ID + "\",\"currency\":\"JPY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_CURRENCY"));
    }

    @Test
    @DisplayName("returns the balance")
    void returnsBalance() throws Exception {
        when(walletService.getBalance(WALLET_ID)).thenReturn(
                new BalanceResponse(WALLET_ID, new BigDecimal("125.50"), "INR", Instant.now()));

        mockMvc.perform(get("/api/v1/wallets/{id}/balance", WALLET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(125.50))
                .andExpect(jsonPath("$.data.currency").value("INR"));
    }

    @Test
    @DisplayName("an unknown wallet is a 404")
    void unknownWallet() throws Exception {
        when(walletService.getBalance(WALLET_ID)).thenThrow(new WalletNotFoundException(WALLET_ID));

        mockMvc.perform(get("/api/v1/wallets/{id}/balance", WALLET_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WALLET_NOT_FOUND"));
    }

    @Test
    @DisplayName("a path variable that is not a UUID is a 400, not a 500")
    void rejectsMalformedId() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/{id}/balance", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("an empty ledger is an empty list, not an error")
    void emptyLedger() throws Exception {
        when(walletService.getLedger(WALLET_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/wallets/{id}/ledger", WALLET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}