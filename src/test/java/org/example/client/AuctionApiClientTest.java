package org.example.client;

import com.sun.net.httpserver.HttpServer;
import org.example.model.WalletSummary;
import org.example.server.ApiJson;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionApiClientTest {
    @Test
    void connectivityFailureIsTrueForIoExceptions() {
        AuctionApiClient.ApiClientException failure =
                new AuctionApiClient.ApiClientException("Could not reach server", new IOException("Connection refused"));

        assertTrue(AuctionApiClient.isConnectivityFailure(failure));
    }

    @Test
    void connectivityFailureIsTrueForNestedIoExceptions() {
        AuctionApiClient.ApiClientException failure = new AuctionApiClient.ApiClientException(
                "Could not reach server",
                new IllegalStateException("wrapper", new IOException("Connection refused"))
        );

        assertTrue(AuctionApiClient.isConnectivityFailure(failure));
    }

    @Test
    void connectivityFailureIsFalseForApplicationErrors() {
        AuctionApiClient.ApiClientException failure =
                new AuctionApiClient.ApiClientException("Password does not match.");

        assertFalse(AuctionApiClient.isConnectivityFailure(failure));
    }

    @Test
    void addWalletAccountSendsInitialBalance() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/api/users/me/wallet/accounts", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = ApiJson.stringify(walletResponse()).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, response.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(response);
            }
        });
        server.start();

        String previousBaseUrl = System.getProperty("auction.api.baseUrl");
        try {
            System.setProperty("auction.api.baseUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/api");
            AuctionApiClient client = newApiClient();

            WalletSummary summary = client.addWalletAccount(
                    "token",
                    "Savings",
                    "Demo Bank",
                    "1234567890",
                    125.5,
                    true,
                    "2468"
            );

            Map<String, Object> payload = ApiJson.parseObject(requestBody.get());
            assertEquals(125.5, ((Number) payload.get("initialBalance")).doubleValue());
            assertEquals(125.5, summary.linkedAccounts().getFirst().balance());
        } finally {
            if (previousBaseUrl == null) {
                System.clearProperty("auction.api.baseUrl");
            } else {
                System.setProperty("auction.api.baseUrl", previousBaseUrl);
            }
            server.stop(0);
        }
    }

    @Test
    void nonJsonErrorResponsesStillProduceHelpfulFailures() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/auth/me", exchange -> {
            byte[] response = "temporary upstream failure".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(502, response.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(response);
            }
        });
        server.start();

        String previousBaseUrl = System.getProperty("auction.api.baseUrl");
        try {
            System.setProperty("auction.api.baseUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/api");
            AuctionApiClient client = newApiClient();

            AuctionApiClient.ApiClientException exception = assertThrows(
                    AuctionApiClient.ApiClientException.class,
                    () -> client.getCurrentUser("token")
            );

            assertEquals("temporary upstream failure", exception.getMessage());
            assertEquals(502, exception.getStatusCode());
        } finally {
            if (previousBaseUrl == null) {
                System.clearProperty("auction.api.baseUrl");
            } else {
                System.setProperty("auction.api.baseUrl", previousBaseUrl);
            }
            server.stop(0);
        }
    }

    private AuctionApiClient newApiClient() throws Exception {
        Constructor<AuctionApiClient> constructor = AuctionApiClient.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private Map<String, Object> walletResponse() {
        return Map.of(
                "wallet", Map.of(
                        "userId", "user-1",
                        "balance", 0.0,
                        "lockedBalance", 0.0,
                        "availableBalance", 0.0,
                        "pinSet", true,
                        "linkedAccounts", List.of(Map.of(
                                "id", "account-1",
                                "userId", "user-1",
                                "accountName", "Savings",
                                "providerName", "Demo Bank",
                                "accountReference", "1234567890",
                                "balance", 125.5,
                                "primary", true,
                                "createdAt", "2026-05-20T00:00:00"
                        )),
                        "transactions", List.of()
                )
        );
    }
}
