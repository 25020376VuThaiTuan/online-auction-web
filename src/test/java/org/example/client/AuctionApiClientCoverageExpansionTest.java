package org.example.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.example.auction.AuctionStatus;
import org.example.auction.BidValidationResult;
import org.example.client.AuctionApiClient.ApiClientException;
import org.example.model.ApprovalStatus;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.model.User;
import org.example.model.WalletAuthorization;
import org.example.model.WalletRecoveryResult;
import org.example.model.WalletSummary;
import org.example.model.WalletTransaction;
import org.example.server.ApiJson;
import org.example.viewmodel.AuctionEligibilityEntry;
import org.example.viewmodel.AuctionListEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionApiClientCoverageExpansionTest {
    private HttpServer server;
    private String previousBaseUrl;
    private final List<RequestRecord> requests = new ArrayList<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", this::handle);
        server.start();
        previousBaseUrl = System.getProperty("auction.api.baseUrl");
        System.setProperty("auction.api.baseUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/api/");
    }

    @AfterEach
    void stopServer() {
        if (previousBaseUrl == null) {
            System.clearProperty("auction.api.baseUrl");
        } else {
            System.setProperty("auction.api.baseUrl", previousBaseUrl);
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void clientCoversAuthProfileWalletAuctionItemSettlementAndNotificationEndpoints() throws Exception {
        AuctionApiClient client = newApiClient();
        assertTrue(client.isEnabled());

        AuctionApiClient.AuthResult login = client.login("admin", "secret");
        assertEquals("token-1", login.token());
        assertEquals("ADMIN", login.user().getRole());

        assertEquals("BIDDER", client.registerManualBidder("bidder", "password", "b@test.local", "Bidder").user().getRole());
        assertEquals("SELLER", client.registerManualSeller("seller", "password", "s@test.local", "Seller").user().getRole());
        assertEquals("b@test.local", client.requestPasswordRecovery("bidder", "b@test.local").email());
        assertEquals(
                "Password reset. Sign in with the new password.",
                client.resetPassword("bidder", "b@test.local", "123456", "newpass", "newpass")
        );
        client.logout("token-1");

        User current = client.getCurrentUser("token-1");
        assertInstanceOf(Bidder.class, current);
        assertEquals(25.0, ((Bidder) current).getLockedAmount("ITEM-1"), 0.001);
        assertEquals("Updated User", client.updateProfile("token-1", "Updated User", "123", "Street").getFullName());
        assertEquals("avatar.png", client.updateAvatar("token-1", "avatar.png").getAvatarUrl());
        assertEquals("ADMIN", client.updateUserRole("token-1", "user 1", "ADMIN").getRole());
        assertEquals(2, client.getAllUsers("token-1").size());

        WalletSummary wallet = client.getWallet("token-1", "2468");
        assertEquals(125.5, wallet.balance(), 0.001);
        assertEquals(1, wallet.linkedAccounts().size());
        assertEquals(1, wallet.transactions().size());

        WalletAuthorization authorization = client.authorizeWallet("token-1", "2468");
        assertEquals("wallet-token", authorization.token());
        assertEquals(LocalDateTime.of(2026, 5, 27, 11, 0), authorization.expiresAt());

        assertEquals(125.5, client.setWalletPin("token-1", "1357").balance(), 0.001);
        WalletRecoveryResult recovery = client.requestWalletPinRecovery("token-1");
        assertTrue(recovery.accepted());
        assertEquals("user@test.local", recovery.email());
        assertEquals(125.5, client.resetWalletPin("token-1", "CODE", "9753").balance(), 0.001);
        assertEquals(125.5, client.addWalletAccount("token-1", "Savings", "Bank", "1234", true, "2468").balance(), 0.001);
        assertEquals(125.5, client.setPrimaryWalletAccount("token-1", "account 1", "2468").balance(), 0.001);
        assertEquals(125.5, client.removeWalletAccount("token-1", "account 1", "2468").balance(), 0.001);
        assertEquals(125.5, client.receiveWalletMoney("token-1", "account 1", 10.0, "2468").balance(), 0.001);
        assertEquals(125.5, client.sendWalletMoney("token-1", "account 1", 5.0, "2468").balance(), 0.001);
        assertEquals(125.5, client.topUpWalletAccount("token-1", "account 1", 15.0, "2468").balance(), 0.001);

        Bidder bidder = new Bidder("BIDDER-1", "bidder", "hash", "bidder@test.local", 300.0);
        bidder.lockDeposit("ITEM-OLD", 20.0);
        List<AuctionEligibilityEntry> eligibilityEntries = client.getAuctionEligibilityEntries("token-1", bidder);
        assertEquals(2, eligibilityEntries.size());
        assertTrue(eligibilityEntries.getFirst().isEligible());
        List<AuctionListEntry> listEntries = client.getAuctionListEntries("token-1");
        assertEquals("Vintage Camera", listEntries.getFirst().getItemName());

        AuctionApiClient.AuctionDetail auction = client.getAuction("token-1", "item 1");
        assertEquals("RUNNING", auction.status());
        assertEquals(2, client.getBidHistory("token-1", "item 1").size());

        Item added = client.addSellerItem(
                "token-1",
                "vehicle",
                "Roadster",
                "Fast",
                1_000.0,
                LocalDateTime.of(2026, 5, 27, 9, 0),
                LocalDateTime.of(2026, 5, 27, 10, 0),
                "Model",
                100
        );
        assertEquals("Roadster", added.getItemName());
        assertEquals(1, client.getSellerItems("token-1").size());
        assertEquals(1, client.getPendingApprovalItems("token-1").size());
        assertEquals(ApprovalStatus.APPROVED, client.updateItemApproval("token-1", "item 1", ApprovalStatus.APPROVED).getApprovalStatus());

        BidValidationResult bid = client.placeBid("token-1", "item 1", 200.0, "2468");
        assertTrue(bid.accepted());
        assertEquals(AuctionStatus.RUNNING, bid.status());
        assertTrue(client.confirmAuctionEntry("token-1", "item 1", "2468").result().accepted());
        client.registerAutoBid("token-1", "item 1", 500.0, 25.0, "2468");
        assertTrue(client.disableAutoBid("token-1", "item 1", "2468"));
        client.startAuction("token-1", "item 1");
        client.finishAuction("token-1", "item 1");

        assertTrue(client.placeBid("token-1", "item 1", 205.0).accepted());
        assertTrue(client.confirmAuctionEntry("token-1", "item 1").result().accepted());
        client.registerAutoBid("token-1", "item 1", 525.0);
        client.registerAutoBid("token-1", "item 1", 550.0, "2468");

        assertEquals("AWAITING_WINNER_ADMISSION", client.getSettlement("token-1", "item 1").status());
        assertEquals("AWAITING_WINNER_ADMISSION", client.admitWinnerResult("token-1", "item 1", "2468").status());
        assertEquals("AWAITING_WINNER_ADMISSION", client.markGoodsShipped("token-1", "item 1", "2468").status());
        assertEquals("AWAITING_WINNER_ADMISSION", client.confirmGoodsReceived("token-1", "item 1", "2468").status());
        assertEquals("AWAITING_WINNER_ADMISSION", client.reportGoodsNotReceived("token-1", "item 1", "missing", "2468").status());
        assertEquals("AWAITING_WINNER_ADMISSION", client.adminUnfreezePayment("token-1", "item 1", "2468").status());
        assertEquals("AWAITING_WINNER_ADMISSION", client.adminKeepPaymentFrozen("token-1", "item 1", "2468").status());
        assertEquals("AWAITING_WINNER_ADMISSION", client.admitWinnerResult("token-1", "item 1").status());
        assertEquals("AWAITING_WINNER_ADMISSION", client.markGoodsShipped("token-1", "item 1").status());
        assertEquals("AWAITING_WINNER_ADMISSION", client.confirmGoodsReceived("token-1", "item 1").status());
        assertEquals("AWAITING_WINNER_ADMISSION", client.reportGoodsNotReceived("token-1", "item 1", "missing").status());
        assertEquals("AWAITING_WINNER_ADMISSION", client.adminUnfreezePayment("token-1", "item 1").status());
        assertEquals("AWAITING_WINNER_ADMISSION", client.adminKeepPaymentFrozen("token-1", "item 1").status());
        assertEquals(1, client.getSettlements("token-1").size());

        List<AuctionApiClient.NotificationDetail> notificationDetails = client.getNotificationDetails("token-1");
        assertEquals("Payment released|Funds sent|", notificationDetails.getFirst().popupKey());
        assertEquals("Payment released: Funds sent", notificationDetails.getFirst().displayText());
        assertEquals(List.of("Payment released: Funds sent"), client.getNotifications("token-1"));

        List<WalletTransaction> auditTransactions = client.getWalletAuditTransactions("token-1", "user 1");
        assertEquals(1, auditTransactions.size());
        assertNull(auditTransactions.getFirst().createdAt());

        assertTrue(requests.stream().anyMatch(request -> request.path().equals("/api/users/user%201/role")));
        assertTrue(requests.stream().anyMatch(request -> request.path().equals("/api/auctions/item%201")));
        assertTrue(requests.stream().anyMatch(request -> request.body().contains("\"bidIncrement\":25.0")));
        assertTrue(requests.stream().anyMatch(request -> request.authorization().equals("Bearer token-1")));
    }

    @Test
    void clientHandlesNullSettlementAndStructuredFailures() throws Exception {
        AuctionApiClient client = newApiClient();

        assertNull(client.getSettlement("token", "missing-settlement"));

        ApiClientException exception = assertThrows(ApiClientException.class, () -> client.getAuction("token", "invalid-json"));
        assertEquals(200, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("API request failed"));

        ApiClientException longError = assertThrows(ApiClientException.class, () -> client.getAuction("token", "long-error"));
        assertEquals(400, longError.getStatusCode());
        assertTrue(longError.getMessage().length() <= 240);
    }

    private void handle(HttpExchange exchange) throws java.io.IOException {
        String path = exchange.getRequestURI().getPath();
        String rawPath = exchange.getRequestURI().getRawPath();
        String method = exchange.getRequestMethod();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        requests.add(new RequestRecord(method, rawPath, body, authorization == null ? "" : authorization));

        Object payload;
        int status = 200;
        String relativePath = path.substring("/api".length());
        if (relativePath.equals("/auctions/invalid-json")) {
            write(exchange, 200, "{not-json");
            return;
        }
        if (relativePath.equals("/auctions/long-error")) {
            payload = Map.of("error", "x".repeat(300));
            status = 400;
        } else {
            payload = responseFor(method, relativePath, body);
        }
        write(exchange, status, ApiJson.stringify(payload));
    }

    private Map<String, Object> responseFor(String method, String path, String body) {
        if (path.equals("/auth/login")) {
            return Map.of("token", "token-1", "user", user("ADMIN"));
        }
        if (path.equals("/auth/register")) {
            return Map.of("token", "token-1", "user", user(body.contains("\"role\":\"SELLER\"") ? "SELLER" : "BIDDER"));
        }
        if (path.equals("/auth/password/recovery")) {
            return Map.of("recovery", Map.of("accepted", true, "message", "sent", "email", "b@test.local"));
        }
        if (path.equals("/auth/password/reset")) {
            return Map.of("message", "Password reset. Sign in with the new password.");
        }
        if (path.equals("/auth/me")) {
            return Map.of("user", user("BIDDER"));
        }
        if (path.equals("/auth/logout")) {
            return Map.of("ok", true);
        }
        if (path.equals("/users/me/wallet/authorization")) {
            return Map.of("authorization", Map.of("token", "wallet-token", "expiresAt", "2026-05-27T11:00:00"));
        }
        if (path.equals("/users/me/wallet/recovery")) {
            return Map.of("recovery", Map.of("accepted", "true", "message", "sent", "email", "user@test.local"));
        }
        if (path.startsWith("/users/me/wallet")) {
            return Map.of("wallet", wallet());
        }
        if (path.equals("/users")) {
            return Map.of("users", List.of(user("BIDDER"), user("SELLER")));
        }
        if (path.startsWith("/users/") && path.endsWith("/role")) {
            return Map.of("user", user("ADMIN"));
        }
        if (path.endsWith("/wallet/transactions")) {
            return Map.of("transactions", List.of(transaction("null")));
        }
        if (path.equals("/auctions")) {
            return Map.of("auctions", List.of(auction("RUNNING", true), auction("FINISHED", false)));
        }
        if (path.endsWith("/bids") && "GET".equals(method)) {
            return Map.of("bids", List.of(bid("BID-1"), bid("BID-2")));
        }
        if (path.endsWith("/entry-deposit")) {
            return new LinkedHashMap<>(Map.of(
                    "accepted", true,
                    "message", "locked",
                    "requiredDeposit", 25.0,
                    "lockedDeposit", 25.0,
                    "status", "RUNNING",
                    "user", user("BIDDER")
            ));
        }
        if (path.endsWith("/auto-bid")) {
            return method.equals("DELETE") ? Map.of("disabled", "true") : Map.of("ok", true);
        }
        if (path.endsWith("/bids")) {
            return bidResult();
        }
        if (path.endsWith("/start") || path.endsWith("/finish")) {
            return Map.of("ok", true);
        }
        if (path.equals("/settlements")) {
            return Map.of("settlements", List.of(settlement()));
        }
        if (path.contains("/settlement")) {
            if (path.equals("/auctions/missing-settlement/settlement")) {
                return Map.of();
            }
            return Map.of("settlement", settlement());
        }
        if (path.equals("/notifications")) {
            return Map.of("notifications", List.of(Map.of(
                    "title", "Payment released",
                    "body", "Funds sent",
                    "displayText", ""
            )));
        }
        if (path.equals("/items/seller") || path.equals("/items/pending")) {
            return Map.of("items", List.of(item("art", "PENDING")));
        }
        if (path.equals("/items") || path.startsWith("/items/")) {
            return Map.of("item", item("vehicle", "APPROVED"));
        }
        if (path.startsWith("/auctions/")) {
            return Map.of("auction", auction("RUNNING", true));
        }
        return Map.of("user", user("BIDDER"));
    }

    private static Map<String, Object> user(String role) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", role + "-1");
        user.put("username", role.toLowerCase());
        user.put("email", role.toLowerCase() + "@test.local");
        user.put("role", role);
        user.put("fullName", "Updated User");
        user.put("phoneNumber", "123");
        user.put("address", "Street");
        user.put("avatarUrl", "avatar.png");
        user.put("balance", "250.50");
        user.put("lockedDeposits", Map.of("ITEM-1", "25.0"));
        user.put("wallet", wallet());
        return user;
    }

    private static Map<String, Object> wallet() {
        return Map.of(
                "userId", "BIDDER-1",
                "balance", "125.5",
                "lockedBalance", "10.0",
                "availableBalance", "115.5",
                "pinSet", "true",
                "linkedAccounts", List.of(Map.of(
                        "id", "account-1",
                        "userId", "BIDDER-1",
                        "accountName", "Savings",
                        "providerName", "Bank",
                        "accountReference", "1234567890",
                        "balance", "125.5",
                        "primary", "true",
                        "createdAt", "2026-05-27T10:00:00"
                )),
                "transactions", List.of(transaction("2026-05-27T10:05:00"))
        );
    }

    private static Map<String, Object> transaction(String createdAt) {
        return Map.of(
                "id", "TX-1",
                "userId", "BIDDER-1",
                "transactionType", "TOP_UP",
                "amount", "10.0",
                "balanceBefore", "115.5",
                "balanceAfter", "125.5",
                "referenceId", "REF-1",
                "note", "note",
                "createdAt", createdAt
        );
    }

    private static Map<String, Object> auction(String status, boolean eligible) {
    return Map.ofEntries(
            Map.entry("itemId", "ITEM-1"),
            Map.entry("itemName", "Vintage Camera"),
            Map.entry("description", "Mirrorless"),
            Map.entry("status", status),
            Map.entry("currentPrice", "100.0"),
            Map.entry("minimumNextBid", "110.0"),
            Map.entry("requiredDeposit", "25.0"),
            Map.entry("depositConfirmed", "false"),
            Map.entry("eligible", String.valueOf(eligible)),
            Map.entry("availableBalance", "300.0"),
            Map.entry("secondsRemaining", "60"),
            Map.entry("displayEndTime", "27/05/2026 11:00")
    );
}

    private static Map<String, Object> bid(String id) {
        return Map.of(
                "id", id,
                "bidderId", "BIDDER-1",
                "itemId", "ITEM-1",
                "amount", "120.0",
                "bidTime", "2026-05-27T10:10:00"
        );
    }

    private static Map<String, Object> bidResult() {
        return Map.of(
                "accepted", "true",
                "message", "accepted",
                "attemptedAmount", "200.0",
                "currentPrice", "100.0",
                "minimumAllowedBid", "110.0",
                "status", "RUNNING",
                "effectiveEndTime", "2026-05-27T11:00:00"
        );
    }

    private static Map<String, Object> item(String itemType, String approvalStatus) {
        return Map.of(
                "itemType", itemType,
                "itemId", "ITEM-1",
                "itemName", "Roadster",
                "description", "Fast",
                "startingPrice", "1000.0",
                "currentPrice", "1000.0",
                "sellerId", "SELLER-1",
                "approvalStatus", approvalStatus,
                "startTime", "2026-05-27T09:00:00",
                "endTime", "2026-05-27T10:00:00"
        );
    }

    private static Map<String, Object> settlement() {
    return Map.ofEntries(
            Map.entry("itemId", "ITEM-1"),
            Map.entry("itemName", "Vintage Camera"),
            Map.entry("sellerId", "SELLER-1"),
            Map.entry("status", "AWAITING_WINNER_ADMISSION"),
            Map.entry("winnerBidderId", "BIDDER-1"),
            Map.entry("winningBidAmount", "200.0"),
            Map.entry("depositAmount", "25.0"),
            Map.entry("buyerPremiumAmount", "10.0"),
            Map.entry("totalBuyerDue", "210.0"),
            Map.entry("remainingPaymentDue", "185.0"),
            Map.entry("adminCommission", "20.0"),
            Map.entry("sellerPayout", "180.0"),
            Map.entry("lockedRemainingPayment", "185.0"),
            Map.entry("sellerReleasedAmount", "0.0"),
            Map.entry("buyerRefundedAmount", "0.0"),
            Map.entry("buyerConfirmationDeadline", "2026-05-28T10:00:00"),
            Map.entry("displaySummary", "summary")
    );
}

    private void write(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        try (var output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    private AuctionApiClient newApiClient() throws Exception {
        Constructor<AuctionApiClient> constructor = AuctionApiClient.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private record RequestRecord(String method, String path, String body, String authorization) {
    }
}
