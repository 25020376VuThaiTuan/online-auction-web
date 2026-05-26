package org.example.server;

import com.sun.net.httpserver.HttpServer;
import org.example.service.AuthenticationService;
import org.example.service.AuctionWorkflowService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalletApiIntegrationTest {
    private HttpServer server;
    private String baseUrl;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", new AuctionApiHandler(
                AuthenticationService.getInstance(),
                AuctionWorkflowService.getInstance(),
                new ApiSessionService(),
                new AuctionRealtimeBroker()
        ));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void walletAccountCreationUsesOpeningBalanceForTransfersAndRemovalUsesApiPinFlow() throws Exception {
        LoginResult login = login();
        String token = login.token();
        request("PATCH", "/users/me/wallet/pin", token, Map.of("newPin", "2468"));

        Map<String, Object> accountResponse = request("POST", "/users/me/wallet/accounts", token, Map.of(
                "accountName", login.fullName(),
                "providerName", "Integration Provider",
                "accountReference", "1234567890",
                "initialBalance", 30.0,
                "primary", true,
                "walletPin", "2468"
        ));
        Map<?, ?> wallet = (Map<?, ?>) accountResponse.get("wallet");
        Map<?, ?> account = (Map<?, ?>) ((java.util.List<?>) wallet.get("linkedAccounts")).getFirst();
        String accountId = String.valueOf(account.get("id"));
        assertEquals(30.0, ((Number) account.get("balance")).doubleValue());
        assertEquals(0.0, ((Number) wallet.get("balance")).doubleValue());

        Map<String, Object> topUpResponse = request("POST", "/users/me/wallet/top-up", token, Map.of(
                "accountId", accountId,
                "amount", 20.0,
                "walletPin", "2468"
        ));
        Map<?, ?> toppedUpWallet = (Map<?, ?>) topUpResponse.get("wallet");
        Map<?, ?> toppedUpAccount = (Map<?, ?>) ((java.util.List<?>) toppedUpWallet.get("linkedAccounts")).getFirst();
        assertEquals(20.0, ((Number) toppedUpWallet.get("balance")).doubleValue());
        assertEquals(10.0, ((Number) toppedUpAccount.get("balance")).doubleValue());

        Map<String, Object> removeResponse = request("DELETE", "/users/me/wallet/accounts/" + accountId, token, Map.of(
                "walletPin", "2468"
        ));
        assertTrue(String.valueOf(removeResponse.get("message")).contains("removed"));
    }

    @Test
    void walletPinCanOnlyBeInitializedOnceThroughApi() throws Exception {
        String token = login().token();
        request("PATCH", "/users/me/wallet/pin", token, Map.of("newPin", "1357"));

        HttpResponse<String> response = rawRequest("PATCH", "/users/me/wallet/pin", token, Map.of("newPin", "9753"));

        assertEquals(409, response.statusCode());
        assertTrue(response.body().contains("Wallet PIN can only be set once"));
    }

    @Test
    void recoveryEndpointDoesNotExposeRecoveryCodeByDefault() throws Exception {
        String token = login().token();

        Map<String, Object> response = request("POST", "/users/me/wallet/recovery", token, Map.of());
        Map<?, ?> recovery = (Map<?, ?>) response.get("recovery");

        assertFalse(recovery.containsKey("recoveryCode"));
    }

    @Test
    void corsOnlyAllowsConfiguredOrigins() throws Exception {
        String previousOrigin = System.getProperty("auction.api.allowedOrigin");
        try {
            System.setProperty("auction.api.allowedOrigin", "http://auction.example.test");

            HttpResponse<String> rejected = rawOptions("/health", "http://evil.example.test");
            assertEquals(204, rejected.statusCode());
            assertTrue(rejected.headers().firstValue("Access-Control-Allow-Origin").isEmpty());

            HttpResponse<String> accepted = rawOptions("/health", "http://auction.example.test");
            assertEquals(204, accepted.statusCode());
            assertEquals(
                    "http://auction.example.test",
                    accepted.headers().firstValue("Access-Control-Allow-Origin").orElse("")
            );
        } finally {
            if (previousOrigin == null) {
                System.clearProperty("auction.api.allowedOrigin");
            } else {
                System.setProperty("auction.api.allowedOrigin", previousOrigin);
            }
        }
    }

    @Test
    void sseEndpointRejectsQueryTokens() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/events/stream?token=query-token"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("Missing bearer token."));
    }

    @Test
    void registeredBidderStartsWithZeroBalance() throws Exception {
        LoginResult login = login();

        assertEquals(0.0, login.balance());
    }

    private LoginResult login() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String fullName = "Integration Bidder " + suffix;
        Map<String, Object> response = request("POST", "/auth/register", null, Map.of(
                "username", "bidder_" + suffix,
                "password", "bid123",
                "email", "bidder_" + suffix + "@test.local",
                "fullName", fullName
        ));
        Map<?, ?> user = (Map<?, ?>) response.get("user");
        return new LoginResult(
                String.valueOf(response.get("token")),
                String.valueOf(user.get("id")),
                ((Number) user.get("balance")).doubleValue(),
                fullName
        );
    }

    private Map<String, Object> request(String method, String path, String token, Map<String, Object> body) throws Exception {
        HttpResponse<String> response = rawRequest(method, path, token, body);
        if (response.statusCode() >= 400) {
            throw new AssertionError("HTTP " + response.statusCode() + ": " + response.body());
        }
        return ApiJson.parseObject(response.body());
    }

    private HttpResponse<String> rawRequest(String method, String path, String token, Map<String, Object> body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        builder.method(method, HttpRequest.BodyPublishers.ofString(ApiJson.stringify(body)));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> rawOptions(String path, String origin) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Origin", origin)
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private record LoginResult(String token, String userId, double balance, String fullName) {
    }
}
