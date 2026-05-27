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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

        Map<String, Object> accountTopUpResponse = request("POST", "/users/me/wallet/accounts/" + accountId + "/top-up", token, Map.of(
                "amount", 10.0,
                "walletPin", "2468"
        ));
        Map<?, ?> accountToppedUpWallet = (Map<?, ?>) accountTopUpResponse.get("wallet");
        Map<?, ?> accountToppedUp = (Map<?, ?>) ((java.util.List<?>) accountToppedUpWallet.get("linkedAccounts")).getFirst();
        assertEquals(40.0, ((Number) accountToppedUp.get("balance")).doubleValue());
        assertEquals(0.0, ((Number) accountToppedUpWallet.get("balance")).doubleValue());

        Map<String, Object> topUpResponse = request("POST", "/users/me/wallet/top-up", token, Map.of(
                "accountId", accountId,
                "amount", 20.0,
                "walletPin", "2468"
        ));
        Map<?, ?> toppedUpWallet = (Map<?, ?>) topUpResponse.get("wallet");
        Map<?, ?> toppedUpAccount = (Map<?, ?>) ((java.util.List<?>) toppedUpWallet.get("linkedAccounts")).getFirst();
        assertEquals(20.0, ((Number) toppedUpWallet.get("balance")).doubleValue());
        assertEquals(20.0, ((Number) toppedUpAccount.get("balance")).doubleValue());

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

    @Test
    void userProfileWalletAuthorizationWithdrawalAdminAuditAndLogoutRoutesWorkTogether() throws Exception {
        LoginResult login = login();
        String token = login.token();

        Map<String, Object> health = ApiJson.parseObject(rawNoBody("GET", "/health", null).body());
        assertEquals("ok", health.get("status"));

        Map<String, Object> profile = request("PATCH", "/users/me/profile", token, Map.of(
                "fullName", login.fullName() + " Updated",
                "phoneNumber", "555-0200",
                "address", "Integration Street"
        ));
        Map<?, ?> profileUser = (Map<?, ?>) profile.get("user");
        assertEquals(login.fullName() + " Updated", profileUser.get("fullName"));

        Map<String, Object> avatar = request("PATCH", "/users/me/avatar", token, Map.of(
                "avatarUrl", "https://example.test/avatar.png"
        ));
        Map<?, ?> avatarUser = (Map<?, ?>) avatar.get("user");
        assertEquals("https://example.test/avatar.png", avatarUser.get("avatarUrl"));

        request("PATCH", "/users/me/wallet/pin", token, Map.of("newPin", "8642"));
        Map<String, Object> authorization = request("POST", "/users/me/wallet/authorization", token, Map.of("walletPin", "8642"));
        Map<?, ?> authorizationBody = (Map<?, ?>) authorization.get("authorization");
        String walletAuthorization = String.valueOf(authorizationBody.get("token"));
        assertTrue(walletAuthorization.startsWith("wa_"));

        Map<String, Object> accountResponse = request("POST", "/users/me/wallet/accounts", token, Map.of(
                "accountName", login.fullName() + " Updated",
                "providerName", "Audit Provider",
                "accountReference", "9988776655",
                "initialBalance", 60.0,
                "primary", true,
                "walletPin", walletAuthorization
        ));
        Map<?, ?> wallet = (Map<?, ?>) accountResponse.get("wallet");
        Map<?, ?> account = (Map<?, ?>) ((java.util.List<?>) wallet.get("linkedAccounts")).getFirst();
        String accountId = String.valueOf(account.get("id"));

        request("POST", "/users/me/wallet/top-up", token, Map.of(
                "accountId", accountId,
                "amount", 25.0,
                "walletPin", walletAuthorization
        ));
        Map<String, Object> withdrawal = request("POST", "/users/me/wallet/withdraw", token, Map.of(
                "accountId", accountId,
                "amount", 10.0,
                "walletPin", walletAuthorization
        ));
        Map<?, ?> withdrawnWallet = (Map<?, ?>) withdrawal.get("wallet");
        assertEquals(15.0, ((Number) withdrawnWallet.get("balance")).doubleValue());

        Map<String, Object> openedWallet = request("POST", "/users/me/wallet", token, Map.of("walletPin", walletAuthorization));
        assertEquals(15.0, ((Number) ((Map<?, ?>) openedWallet.get("wallet")).get("balance")).doubleValue());

        LoginResult admin = adminLogin();
        Map<String, Object> roleResponse = request("PATCH", "/users/" + login.userId() + "/role", admin.token(), Map.of("role", "SELLER"));
        assertEquals("SELLER", ((Map<?, ?>) roleResponse.get("user")).get("role"));

        Map<String, Object> audit = request("GET", "/users/" + login.userId() + "/wallet/transactions", admin.token(), Map.of());
        assertTrue(((Number) audit.get("count")).intValue() >= 1);

        Map<String, Object> authMe = ApiJson.parseObject(rawNoBody("GET", "/auth/me", token).body());
        assertEquals(login.userId(), ((Map<?, ?>) authMe.get("user")).get("id"));
        assertEquals(200, rawNoBody("POST", "/auth/logout", token).statusCode());
        assertEquals(401, rawNoBody("GET", "/auth/me", token).statusCode());
    }

    @Test
    void sellerAndAdminItemSettlementAndNotificationRoutesWorkTogether() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> sellerAuth = request("POST", "/auth/register", null, Map.of(
                "username", "seller_" + suffix,
                "password", "sell123",
                "email", "seller_" + suffix + "@test.local",
                "fullName", "Integration Seller " + suffix,
                "role", "SELLER"
        ));
        String sellerToken = String.valueOf(sellerAuth.get("token"));
        String startTime = LocalDateTime.now().minusMinutes(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String endTime = LocalDateTime.now().plusMinutes(30).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        Map<String, Object> created = request("POST", "/items", sellerToken, Map.of(
                "type", "electronics",
                "itemName", "API Camera " + suffix,
                "description", "Camera created through API test",
                "startingPrice", 120.0,
                "startTime", startTime,
                "endTime", endTime,
                "extraText", "Brand",
                "extraNumber", 24
        ));
        Map<?, ?> createdItem = (Map<?, ?>) created.get("item");
        String itemId = String.valueOf(createdItem.get("id"));
        assertEquals("PENDING", createdItem.get("approvalStatus"));

        Map<String, Object> sellerItems = request("GET", "/items/seller", sellerToken, Map.of());
        assertTrue(((java.util.List<?>) sellerItems.get("items")).stream()
                .map(item -> String.valueOf(((Map<?, ?>) item).get("id")))
                .anyMatch(itemId::equals));

        Map<String, Object> allItems = request("GET", "/items", sellerToken, Map.of());
        assertTrue(((Number) allItems.get("count")).intValue() >= 1);

        LoginResult admin = adminLogin();
        Map<String, Object> pending = request("GET", "/items/pending", admin.token(), Map.of());
        assertTrue(((java.util.List<?>) pending.get("items")).stream()
                .map(item -> String.valueOf(((Map<?, ?>) item).get("id")))
                .anyMatch(itemId::equals));

        Map<String, Object> approved = request("PATCH", "/items/" + itemId + "/approval", admin.token(), Map.of(
                "approvalStatus", "APPROVED"
        ));
        assertEquals("APPROVED", ((Map<?, ?>) approved.get("item")).get("approvalStatus"));

        Map<String, Object> settlements = request("GET", "/settlements", admin.token(), Map.of());
        assertTrue(((Number) settlements.get("count")).intValue() >= 0);
        Map<String, Object> notifications = request("GET", "/notifications", sellerToken, Map.of());
        assertTrue(((Number) notifications.get("count")).intValue() >= 0);
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

    private LoginResult adminLogin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "admin_" + suffix;
        org.example.model.User user = AuthenticationService.getInstance().registerManualBidder(
                username,
                "admin123",
                username + "@test.local",
                "Integration Admin " + suffix
        );
        AuthenticationService.getInstance().updateUserRole(user.getId(), "ADMIN");
        Map<String, Object> response = request("POST", "/auth/login", null, Map.of(
                "username", username,
                "password", "admin123"
        ));
        Map<?, ?> responseUser = (Map<?, ?>) response.get("user");
        return new LoginResult(
                String.valueOf(response.get("token")),
                String.valueOf(responseUser.get("id")),
                ((Number) responseUser.get("balance")).doubleValue(),
                String.valueOf(responseUser.get("fullName"))
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

    private HttpResponse<String> rawNoBody(String method, String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        builder.method(method, HttpRequest.BodyPublishers.noBody());
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
