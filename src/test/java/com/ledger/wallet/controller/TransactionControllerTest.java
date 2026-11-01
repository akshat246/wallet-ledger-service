package com.ledger.wallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.wallet.dto.request.TransferRequest;
import com.ledger.wallet.dto.response.TransactionResult;
import com.ledger.wallet.exception.InsufficientFundsException;
import com.ledger.wallet.exception.WalletNotFoundException;
import com.ledger.wallet.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP contract only: status codes, the error envelope, and the guarantee that a bad
 * request never reaches the service. The service itself is mocked; its behaviour is tested
 * elsewhere.
 */
@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private TransactionService transactionService;

    private static final UUID FROM = UUID.randomUUID();
    private static final UUID TO = UUID.randomUUID();

    private static TransactionResult result(boolean replayed) {
        return new TransactionResult(UUID.randomUUID(), "key-1", "TRANSFER", "COMPLETED",
                new BigDecimal("10.00"), "INR", FROM, TO, replayed, Instant.now());
    }

    private String body(Object request) throws Exception {
        return objectMapper.writeValueAsString(request);
    }

    @Test
    @DisplayName("a first execution returns 201")
    void firstExecutionIsCreated() throws Exception {
        when(transactionService.transfer(eq("key-1"), any())).thenReturn(result(false));

        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new TransferRequest(FROM, TO, new BigDecimal("10.00"), "Dinner"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.replayed").value(false));
    }

    @Test
    @DisplayName("a replay returns 200, because nothing was created")
    void replayIsOk() throws Exception {
        when(transactionService.transfer(eq("key-1"), any())).thenReturn(result(true));

        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new TransferRequest(FROM, TO, new BigDecimal("10.00"), null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replayed").value(true));
    }

    @Test
    @DisplayName("a missing Idempotency-Key is rejected before the service is called")
    void requiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new TransferRequest(FROM, TO, new BigDecimal("10.00"), null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_ERROR"));

        verify(transactionService, never()).transfer(anyString(), any());
    }

    @Test
    @DisplayName("a negative amount fails validation with the offending field named")
    void rejectsNegativeAmount() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new TransferRequest(FROM, TO, new BigDecimal("-5.00"), null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));

        verify(transactionService, never()).transfer(anyString(), any());
    }

    @Test
    @DisplayName("a missing wallet id fails validation")
    void rejectsMissingWallet() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toWalletId\":\"" + TO + "\",\"amount\":10.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("malformed JSON is a 400, not a 500")
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("insufficient funds is a 422: the request was fine, the rule said no")
    void insufficientFundsIsUnprocessable() throws Exception {
        when(transactionService.transfer(anyString(), any()))
                .thenThrow(new InsufficientFundsException(FROM, new BigDecimal("1.00"), new BigDecimal("10.00")));

        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new TransferRequest(FROM, TO, new BigDecimal("10.00"), null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.path").value("/api/v1/transactions/transfer"));
    }

    @Test
    @DisplayName("an unknown wallet is a 404")
    void unknownWalletIsNotFound() throws Exception {
        when(transactionService.transfer(anyString(), any()))
                .thenThrow(new WalletNotFoundException(FROM));

        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new TransferRequest(FROM, TO, new BigDecimal("10.00"), null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WALLET_NOT_FOUND"));
    }

    @Test
    @DisplayName("an unexpected failure is a 500 with no internal detail leaked")
    void hidesInternalErrors() throws Exception {
        when(transactionService.transfer(anyString(), any()))
                .thenThrow(new IllegalStateException("connection pool exhausted at com.zaxxer..."));

        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new TransferRequest(FROM, TO, new BigDecimal("10.00"), null))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
