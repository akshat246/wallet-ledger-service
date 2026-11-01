package com.ledger.wallet.integration;

import com.ledger.wallet.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The same flow a client would run, over real HTTP: create a user, open wallets, fund one,
 * transfer, retry the transfer. Everything below the controller is the real thing.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class WalletApiIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate rest;

    private static Map<String, Object> data(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    private ResponseEntity<Map> post(String path, String json, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(json, headers), Map.class);
    }

    @Test
    @DisplayName("a client can sign up, open wallets, fund one and transfer")
    void fullJourney() {
        String email = "journey-" + UUID.randomUUID() + "@example.com";

        ResponseEntity<Map> user = post("/api/v1/users",
                "{\"email\":\"" + email + "\",\"name\":\"Journey User\",\"phoneNumber\":\"9876543210\"}", null);
        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String userId = (String) data(user).get("id");

        String sender = (String) data(post("/api/v1/wallets",
                "{\"userId\":\"" + userId + "\",\"currency\":\"INR\"}", null)).get("id");
        String receiver = (String) data(post("/api/v1/wallets",
                "{\"userId\":\"" + userId + "\",\"currency\":\"INR\"}", null)).get("id");

        ResponseEntity<Map> deposit = post("/api/v1/transactions/deposit",
                "{\"toWalletId\":\"" + sender + "\",\"amount\":1000.00,\"description\":\"Top-up\"}",
                "api-deposit-" + UUID.randomUUID());
        assertThat(deposit.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String transferKey = "api-transfer-" + UUID.randomUUID();
        String transferBody = "{\"fromWalletId\":\"" + sender + "\",\"toWalletId\":\"" + receiver
                + "\",\"amount\":250.50,\"description\":\"Dinner\"}";

        ResponseEntity<Map> transfer = post("/api/v1/transactions/transfer", transferBody, transferKey);
        assertThat(transfer.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(data(transfer).get("replayed")).isEqualTo(false);

        // The retry a client sends when it never saw the first response.
        ResponseEntity<Map> retry = post("/api/v1/transactions/transfer", transferBody, transferKey);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(retry).get("replayed")).isEqualTo(true);
        assertThat(data(retry).get("transactionId")).isEqualTo(data(transfer).get("transactionId"));

        ResponseEntity<Map> balance = rest.getForEntity("/api/v1/wallets/" + sender + "/balance", Map.class);
        assertThat(((Number) data(balance).get("balance")).doubleValue()).isEqualTo(749.50);
    }

    @Test
    @DisplayName("an overdraft is reported as 422 with a machine-readable code")
    void overdraftIsUnprocessable() {
        UUID wallet = newFundedWallet("10.00");
        UUID other = newWallet();

        ResponseEntity<Map> response = post("/api/v1/transactions/transfer",
                "{\"fromWalletId\":\"" + wallet + "\",\"toWalletId\":\"" + other + "\",\"amount\":100.00}",
                "api-overdraft-" + UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("errorCode")).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    @DisplayName("a duplicate email is reported as 409")
    void duplicateEmailIsConflict() {
        String body = "{\"email\":\"dup-" + UUID.randomUUID() + "@example.com\",\"name\":\"First\"}";

        assertThat(post("/api/v1/users", body, null).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> second = post("/api/v1/users", body, null);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().get("errorCode")).isEqualTo("USER_ALREADY_EXISTS");
    }

    @Test
    @DisplayName("an unknown wallet is reported as 404")
    void unknownWalletIsNotFound() {
        ResponseEntity<Map> response =
                rest.getForEntity("/api/v1/wallets/" + UUID.randomUUID() + "/balance", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("errorCode")).isEqualTo("WALLET_NOT_FOUND");
    }

    @Test
    @DisplayName("the service reports itself healthy")
    void healthEndpoint() {
        ResponseEntity<Map> health = rest.getForEntity("/actuator/health", Map.class);

        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody().get("status")).isEqualTo("UP");
    }
}
