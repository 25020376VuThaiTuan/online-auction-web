package org.example.server;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import org.example.model.ApprovalStatus;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.service.AuthenticationService;
import org.example.service.AuctionWorkflowService;
import org.example.service.MarketplaceDashboardService;
import org.example.service.WalletService;
import org.example.util.CredentialHasher;
import org.example.model.User;
import org.example.repository.DemoUserRepository;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionApiHandlerCoverageTest {
    @Test
    void optionsRequestAppliesCorsHeadersAndClosesWithoutBody() throws Exception {
        String previousOrigin = System.getProperty("auction.api.allowedOrigin");
        try {
            System.setProperty("auction.api.allowedOrigin", "*, http://auction.example.test");
            FakeExchange exchange = exchange("OPTIONS", "/api/health", "");
            exchange.getRequestHeaders().set("Origin", "http://auction.example.test");

            handler().handle(exchange);

            assertEquals(204, exchange.statusCode);
            assertEquals(-1L, exchange.responseLength);
            assertEquals("http://auction.example.test",
                    exchange.getResponseHeaders().getFirst("Access-Control-Allow-Origin"));
            assertEquals("Origin", exchange.getResponseHeaders().getFirst("Vary"));
            assertTrue(exchange.closed);
        } finally {
            if (previousOrigin == null) {
                System.clearProperty("auction.api.allowedOrigin");
            } else {
                System.setProperty("auction.api.allowedOrigin", previousOrigin);
            }
        }
    }

    @Test
    void rootAndUnknownRoutesReturnJsonResponses() throws Exception {
        FakeExchange root = exchange("GET", "/api", "");

        handler().handle(root);

        Map<String, Object> rootPayload = ApiJson.parseObject(root.responseBody());
        assertEquals(200, root.statusCode);
        assertEquals("Online Auction API", rootPayload.get("name"));
        assertTrue(((List<?>) rootPayload.get("endpoints")).contains("/api/health"));

        FakeExchange unknown = exchange("GET", "/api/no%20such", "");
        handler().handle(unknown);

        Map<String, Object> errorPayload = ApiJson.parseObject(unknown.responseBody());
        assertEquals(404, unknown.statusCode);
        assertEquals("Unknown API route.", errorPayload.get("error"));

        AuthenticationService auth = AuthenticationService.getInstance();
        User bidder = auth.registerManualBidder(
                "api_missing_auction_" + UUID.randomUUID().toString().substring(0, 8),
                "secret",
                "api_missing_auction@test.local",
                "API Missing Auction"
        );
        ApiSessionService sessions = new ApiSessionService();
        AuctionApiHandler handler = new AuctionApiHandler(
                auth,
                AuctionWorkflowService.getInstance(),
                sessions,
                new AuctionRealtimeBroker()
        );
        FakeExchange missingAuction = authorizedExchange(
                "GET",
                "/api/auctions/no-such-auction",
                "",
                sessions.createSession(bidder).token()
        );
        handler.handle(missingAuction);
        assertEquals(404, missingAuction.statusCode);

        FakeExchange internalError = exchange("GET", "/api/health", "");
        internalError.failRequestUri = true;
        handler.handle(internalError);
        assertEquals(500, internalError.statusCode);
        assertEquals("Internal server error.", ApiJson.parseObject(internalError.responseBody()).get("error"));
    }

    @Test
    void methodValidationMalformedInputAndLargeBodiesReturnHttpErrors() throws Exception {
        FakeExchange wrongMethod = exchange("POST", "/api/health", "{}");
        handler().handle(wrongMethod);
        assertEquals(405, wrongMethod.statusCode);
        assertEquals("Method not allowed.", ApiJson.parseObject(wrongMethod.responseBody()).get("error"));

        FakeExchange malformedLogin = exchange("POST", "/api/auth/login", "{\"username\":\"bidder\"}");
        handler().handle(malformedLogin);
        assertEquals(400, malformedLogin.statusCode);
        assertEquals("Field 'password' is required.", ApiJson.parseObject(malformedLogin.responseBody()).get("error"));

        String duplicateUsername = "duplicate_api_" + UUID.randomUUID().toString().substring(0, 8);
        AtomicReference<String> deliveredPasswordCode = new AtomicReference<>();
        AuctionApiHandler authHandler = new AuctionApiHandler(
                new AuthenticationService(
                        List.of(DemoUserRepository.getInstance()),
                        (email, recoveryCode) -> deliveredPasswordCode.set(recoveryCode)
                ),
                AuctionWorkflowService.getInstance(),
                new ApiSessionService(),
                new AuctionRealtimeBroker()
        );
        FakeExchange firstRegister = exchange("POST", "/api/auth/register", ApiJson.stringify(Map.of(
                "username", duplicateUsername,
                "password", "secret123",
                "email", duplicateUsername + "@test.local",
                "fullName", "Duplicate API"
        )));
        authHandler.handle(firstRegister);
        assertEquals(201, firstRegister.statusCode, firstRegister.responseBody());
        FakeExchange duplicateRegister = exchange("POST", "/api/auth/register", ApiJson.stringify(Map.of(
                "username", duplicateUsername,
                "password", "secret123",
                "email", duplicateUsername + "@test.local",
                "fullName", "Duplicate API"
        )));
        authHandler.handle(duplicateRegister);
        assertEquals(409, duplicateRegister.statusCode);

        FakeExchange requestRecovery = exchange("POST", "/api/auth/password/recovery", ApiJson.stringify(Map.of(
                "username", duplicateUsername,
                "email", duplicateUsername + "@test.local"
        )));
        authHandler.handle(requestRecovery);
        assertEquals(202, requestRecovery.statusCode);

        FakeExchange resetPassword = exchange("POST", "/api/auth/password/reset", ApiJson.stringify(Map.of(
                "username", duplicateUsername,
                "email", duplicateUsername + "@test.local",
                "recoveryCode", deliveredPasswordCode.get(),
                "newPassword", "updated123",
                "confirmPassword", "updated123"
        )));
        authHandler.handle(resetPassword);
        assertEquals(200, resetPassword.statusCode);

        FakeExchange updatedLogin = exchange("POST", "/api/auth/login", ApiJson.stringify(Map.of(
                "username", duplicateUsername,
                "password", "updated123"
        )));
        authHandler.handle(updatedLogin);
        assertEquals(200, updatedLogin.statusCode);

        FakeExchange largeBody = exchange("POST", "/api/auth/login", "x".repeat(64 * 1024 + 2));
        handler().handle(largeBody);
        assertEquals(413, largeBody.statusCode);
        assertEquals("Request body is too large.", ApiJson.parseObject(largeBody.responseBody()).get("error"));
    }

    @Test
    void authenticationErrorsAndRateLimitAreMappedToJsonResponses() throws Exception {
        AuctionApiHandler handler = handler();
        FakeExchange badCredentials = exchange("POST", "/api/auth/login", ApiJson.stringify(Map.of(
                "username", "missing-user",
                "password", "wrong-password"
        )));

        handler.handle(badCredentials);

        assertEquals(401, badCredentials.statusCode);
        assertEquals("Invalid username or password.", ApiJson.parseObject(badCredentials.responseBody()).get("error"));

        FakeExchange lastAttempt = null;
        for (int attempt = 0; attempt < 11; attempt++) {
            lastAttempt = exchange("POST", "/api/auth/login", ApiJson.stringify(Map.of(
                    "username", "rate-limited-user",
                    "password", "wrong-password"
            )));
            handler.handle(lastAttempt);
        }

        assertEquals(429, lastAttempt.statusCode);
        assertEquals("Too many login attempts. Try again later.",
                ApiJson.parseObject(lastAttempt.responseBody()).get("error"));
    }

    @Test
    void eventStreamRegistersClientAndClosesWhenInitialWriteFails() throws Exception {
        AuthenticationService auth = AuthenticationService.getInstance();
        User user = auth.registerManualBidder(
                "sse_user_" + UUID.randomUUID().toString().substring(0, 8),
                "secret",
                "sse_" + UUID.randomUUID().toString().substring(0, 8) + "@test.local",
                "SSE User"
        );
        ApiSessionService sessions = new ApiSessionService();
        String token = sessions.createSession(user).token();
        AuctionApiHandler handler = new AuctionApiHandler(
                auth,
                AuctionWorkflowService.getInstance(),
                sessions,
                new AuctionRealtimeBroker()
        );
        FakeExchange exchange = exchange("GET", "/api/events/stream", "");
        exchange.getRequestHeaders().set("Authorization", "Bearer " + token);
        exchange.responseBody = new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("client disconnected");
            }
        };

        handler.handle(exchange);

        assertEquals(200, exchange.statusCode);
        assertEquals(0L, exchange.responseLength);
        assertEquals("text/event-stream; charset=UTF-8",
                exchange.getResponseHeaders().getFirst("Content-Type"));
        assertTrue(exchange.closed);
    }

    @Test
    void queryTokenParsingAndOptionalHelpersCoverDecodedBranches() throws Exception {
        AuctionApiHandler handler = handler();
        FakeExchange exchange = exchange("GET", "/api/events/stream?token=query%20token&&flag&empty=", "");

        assertEquals("query token", invoke(handler, "requireToken",
                new Class<?>[]{HttpExchange.class, boolean.class}, exchange, true));

        @SuppressWarnings("unchecked")
        Map<String, String> parameters = (Map<String, String>) invoke(handler, "queryParameters",
                new Class<?>[]{HttpExchange.class}, exchange);
        assertEquals("query token", parameters.get("token"));
        assertEquals("", parameters.get("flag"));
        assertEquals("", parameters.get("empty"));

        @SuppressWarnings("unchecked")
        Map<String, String> emptyParameters = (Map<String, String>) invoke(handler, "queryParameters",
                new Class<?>[]{HttpExchange.class}, exchange("GET", "/api/events/stream", ""));
        assertTrue(emptyParameters.isEmpty());

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", " true ");
        values.put("disabled", false);
        values.put("amount", "12.50");
        values.put("number", 8);
        values.put("initialBalance", "19.75");
        values.put("blank", " ");

        assertEquals(true, invoke(handler, "optionalBoolean",
                new Class<?>[]{Map.class, String.class}, values, "enabled"));
        assertEquals(false, invoke(handler, "optionalBoolean",
                new Class<?>[]{Map.class, String.class}, values, "disabled"));
        assertEquals(12.5, (double) invoke(handler, "optionalDouble",
                new Class<?>[]{Map.class, String.class}, values, "amount"), 0.001);
        assertEquals(8.0, (double) invoke(handler, "optionalDouble",
                new Class<?>[]{Map.class, String.class}, values, "number"), 0.001);
        assertEquals(0.0, (double) invoke(handler, "optionalDouble",
                new Class<?>[]{Map.class, String.class}, values, "blank"), 0.001);
        assertEquals(0.0, (double) invoke(handler, "optionalDouble",
                new Class<?>[]{Map.class, String.class}, values, "missing"), 0.001);
        assertEquals(19.75, (double) invoke(handler, "optionalOpeningBalance",
                new Class<?>[]{Map.class}, values), 0.001);
        values.remove("initialBalance");
        values.put("balance", 4.25);
        assertEquals(4.25, (double) invoke(handler, "optionalOpeningBalance",
                new Class<?>[]{Map.class}, values), 0.001);
        assertEquals(LocalDateTime.of(2026, 5, 27, 12, 30),
                invoke(handler, "parseDateTime", new Class<?>[]{String.class}, "2026-05-27T12:30:00"));
        assertNull(invoke(handler, "parseDateTime", new Class<?>[]{String.class}, " "));
        assertNull(invoke(handler, "formatDateTime", new Class<?>[]{LocalDateTime.class}, new Object[]{null}));

        String previousOrigin = System.getProperty("auction.api.allowedOrigin");
        try {
            System.setProperty("auction.api.allowedOrigin", " ");
            assertNull(invoke(handler, "allowedCorsOrigin",
                    new Class<?>[]{String.class}, "http://not-configured.test"));
        } finally {
            if (previousOrigin == null) {
                System.clearProperty("auction.api.allowedOrigin");
            } else {
                System.setProperty("auction.api.allowedOrigin", previousOrigin);
            }
        }
    }

    @Test
    void protectedRouteFailuresCoverUserAuctionSettlementAndNotificationBranches() throws Exception {
        AuthenticationService auth = AuthenticationService.getInstance();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User bidder = auth.registerManualBidder(
                "api_branch_bidder_" + suffix,
                "secret",
                "api_branch_bidder_" + suffix + "@test.local",
                "API Branch Bidder"
        );
        User admin = auth.registerManualBidder(
                "api_branch_admin_" + suffix,
                "secret",
                "api_branch_admin_" + suffix + "@test.local",
                "API Branch Admin"
        );
        auth.updateUserRole(admin.getId(), "ADMIN");
        admin.setRole("ADMIN");
        ApiSessionService sessions = new ApiSessionService();
        String bidderToken = sessions.createSession(bidder).token();
        String adminToken = sessions.createSession(admin).token();
        AuctionApiHandler handler = new AuctionApiHandler(
                auth,
                AuctionWorkflowService.getInstance(),
                sessions,
                new AuctionRealtimeBroker()
        );

        assertJsonError(handler, "GET", "/api/users", "", bidderToken, 403, "Admin role required.");
        assertJsonError(handler, "GET", "/api/users/me/profile", "", bidderToken, 405, "Method not allowed.");
        assertJsonError(handler, "GET", "/api/users/me/unknown", "", bidderToken, 404, "Unknown user route.");
        assertJsonError(handler, "PATCH", "/api/users/missing-user/role",
                ApiJson.stringify(Map.of("role", "SELLER")), adminToken, 404, "User not found: missing-user");
        assertJsonError(handler, "GET", "/api/notifications/extra", "", bidderToken, 404, "Unknown notification route.");
        assertJsonError(handler, "GET", "/api/settlements/extra", "", bidderToken, 404, "Unknown settlement route.");
        assertJsonError(handler, "POST", "/api/auctions/missing/start", "", bidderToken, 409, "Auction could not be started.");
        assertJsonError(handler, "PUT", "/api/auctions/missing/auto-bid", "", bidderToken, 405, "Method not allowed.");
        assertJsonError(handler, "POST", "/api/auctions/missing/settlement/nope",
                ApiJson.stringify(Map.of("walletPin", "2468")), bidderToken, 404, "Unknown settlement action.");
        assertJsonError(handler, "GET", "/api/auth/nope", "", bidderToken, 404, "Unknown authentication route.");
        assertJsonError(handler, "POST", "/api/events/stream", "", bidderToken, 405, "Method not allowed.");
        assertJsonError(handler, "GET", "/api/events/nope", "", bidderToken, 404, "Unknown event route.");
        assertJsonError(handler, "POST", "/api/items", "{}", bidderToken, 403, "Seller or admin role required.");
        assertJsonError(handler, "GET", "/api/items/seller", "", bidderToken, 403, "Seller or admin role required.");
        assertJsonError(handler, "GET", "/api/items/pending", "", bidderToken, 403, "Admin role required.");
        assertJsonError(handler, "GET", "/api/items/nope", "", bidderToken, 404, "Unknown item route.");
        assertJsonError(handler, "PATCH", "/api/items/missing-item/approval",
                ApiJson.stringify(Map.of("approvalStatus", "APPROVED")), adminToken, 404, "Item not found: missing-item");

        FakeExchange unknownRemote = exchange("POST", "/api/auth/login", ApiJson.stringify(Map.of(
                "username", "no-remote-user",
                "password", "wrong"
        )));
        unknownRemote.remoteAddress = null;
        handler.handle(unknownRemote);

        assertEquals(401, unknownRemote.statusCode);
        assertEquals("Invalid username or password.",
                ApiJson.parseObject(unknownRemote.responseBody()).get("error"));
    }

    @Test
    void auctionAndItemRoutesCoverAcceptedRejectedAndSellerAdminBranches() throws Exception {
        AuthenticationService auth = AuthenticationService.getInstance();
        MarketplaceDashboardService dashboard = MarketplaceDashboardService.getInstance();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Bidder bidder = (Bidder) auth.registerManualBidder(
                "api_auction_bidder_" + suffix,
                "secret",
                "api_auction_bidder_" + suffix + "@test.local",
                "API Auction Bidder"
        );
        User seller = auth.registerManualSeller(
                "api_auction_seller_" + suffix,
                "secret",
                "api_auction_seller_" + suffix + "@test.local",
                "API Auction Seller"
        );
        User admin = auth.registerManualBidder(
                "api_auction_admin_" + suffix,
                "secret",
                "api_auction_admin_" + suffix + "@test.local",
                "API Auction Admin"
        );
        auth.updateUserRole(admin.getId(), "ADMIN");
        admin.setRole("ADMIN");
        bidder.setBalance(500.0);
        auth.updateUser(bidder);
        dashboard.setWalletPin(bidder, "2468");
        dashboard.setWalletPin(seller, "2468");
        ApiSessionService sessions = new ApiSessionService();
        String bidderToken = sessions.createSession(bidder).token();
        String sellerToken = sessions.createSession(seller).token();
        String adminToken = sessions.createSession(admin).token();
        AuctionApiHandler handler = new AuctionApiHandler(
                auth,
                AuctionWorkflowService.getInstance(),
                sessions,
                new AuctionRealtimeBroker()
        );

        Item item = dashboard.addSellerItem(
                seller,
                "electronics",
                "API Route Camera " + suffix,
                "API route coverage item",
                100.0,
                LocalDateTime.now().minusMinutes(2),
                LocalDateTime.now().plusMinutes(20),
                "Brand",
                12
        );
        dashboard.updateItemApproval(item.getId(), ApprovalStatus.APPROVED);
        assertTrue(dashboard.startAuction(seller, item.getId()));

        assertJsonStatus(handler, "GET", "/api/auctions", "", bidderToken, 200);
        assertJsonStatus(handler, "GET", "/api/auctions/" + item.getId(), "", bidderToken, 200);
        assertJsonStatus(handler, "GET", "/api/auctions/" + item.getId() + "/settlement", "", bidderToken, 200);
        assertJsonStatus(handler, "GET", "/api/auctions/" + item.getId() + "/bids", "", bidderToken, 200);

        FakeExchange rejectedBid = authorizedExchange("POST", "/api/auctions/" + item.getId() + "/bids",
                ApiJson.stringify(Map.of("amount", 130.0, "walletPin", "2468")), bidderToken);
        handler.handle(rejectedBid);
        assertEquals(409, rejectedBid.statusCode);
        assertEquals(false, ApiJson.parseObject(rejectedBid.responseBody()).get("accepted"));

        assertJsonError(handler, "POST", "/api/auctions/" + item.getId() + "/auto-bid",
                ApiJson.stringify(Map.of("maxLimit", 220.0, "bidIncrement", 10.0, "walletPin", "2468")),
                bidderToken,
                409,
                "Confirm entry deposit and make sure available balance covers the auto-bid maximum.");

        FakeExchange inactiveAutoBid = authorizedExchange("DELETE", "/api/auctions/" + item.getId() + "/auto-bid",
                ApiJson.stringify(Map.of("walletPin", "2468")), bidderToken);
        handler.handle(inactiveAutoBid);
        assertEquals(200, inactiveAutoBid.statusCode);
        assertEquals(false, ApiJson.parseObject(inactiveAutoBid.responseBody()).get("disabled"));

        FakeExchange deposit = authorizedExchange("POST", "/api/auctions/" + item.getId() + "/entry-deposit",
                ApiJson.stringify(Map.of("walletPin", "2468")), bidderToken);
        handler.handle(deposit);
        assertEquals(201, deposit.statusCode);
        assertEquals(true, ApiJson.parseObject(deposit.responseBody()).get("accepted"));

        FakeExchange acceptedBid = authorizedExchange("POST", "/api/auctions/" + item.getId() + "/bids",
                ApiJson.stringify(Map.of("amount", 130.0, "walletPin", "2468")), bidderToken);
        handler.handle(acceptedBid);
        assertEquals(201, acceptedBid.statusCode);
        assertEquals(true, ApiJson.parseObject(acceptedBid.responseBody()).get("accepted"));

        assertJsonStatus(handler, "POST", "/api/auctions/" + item.getId() + "/auto-bid",
                ApiJson.stringify(Map.of("maxLimit", 220.0, "walletPin", "2468")), bidderToken, 201);
        FakeExchange activeAutoBid = authorizedExchange("DELETE", "/api/auctions/" + item.getId() + "/auto-bid",
                ApiJson.stringify(Map.of("walletPin", "2468")), bidderToken);
        handler.handle(activeAutoBid);
        assertEquals(200, activeAutoBid.statusCode);
        assertEquals(true, ApiJson.parseObject(activeAutoBid.responseBody()).get("disabled"));

        assertJsonStatus(handler, "POST", "/api/auctions/" + item.getId() + "/finish", "", sellerToken, 200);

        Item startable = dashboard.addSellerItem(
                seller,
                "electronics",
                "API Start Camera " + suffix,
                "API start route coverage item",
                80.0,
                LocalDateTime.now().minusMinutes(2),
                LocalDateTime.now().plusMinutes(10),
                "Brand",
                8
        );
        dashboard.updateItemApproval(startable.getId(), ApprovalStatus.APPROVED);
        assertJsonStatus(handler, "POST", "/api/auctions/" + startable.getId() + "/start", "", sellerToken, 200);

        FakeExchange createItem = authorizedExchange("POST", "/api/items", ApiJson.stringify(Map.of(
                "type", "electronics",
                "itemName", "API Created Camera " + suffix,
                "description", "Created through API handler test",
                "startingPrice", 90.0,
                "startTime", LocalDateTime.now().plusMinutes(1).toString(),
                "endTime", LocalDateTime.now().plusMinutes(31).toString(),
                "extraText", "Brand",
                "extraNumber", 10.0
        )), sellerToken);
        handler.handle(createItem);
        assertEquals(201, createItem.statusCode);

        assertJsonStatus(handler, "GET", "/api/items", "", bidderToken, 200);
        assertJsonStatus(handler, "GET", "/api/items/seller", "", sellerToken, 200);
        assertJsonStatus(handler, "GET", "/api/items/pending", "", adminToken, 200);
    }

    @Test
    void userWalletRoutesCoverProfilePinRecoveryAccountsTransfersAndAudit() throws Exception {
        AuthenticationService auth = AuthenticationService.getInstance();
        MarketplaceDashboardService dashboard = MarketplaceDashboardService.getInstance();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Bidder bidder = (Bidder) auth.registerManualBidder(
                "api_wallet_bidder_" + suffix,
                "secret",
                "api_wallet_bidder_" + suffix + "@test.local",
                "API Wallet Bidder"
        );
        bidder.setBalance(500.0);
        auth.updateUser(bidder);
        User admin = auth.registerManualBidder(
                "api_wallet_admin_" + suffix,
                "secret",
                "api_wallet_admin_" + suffix + "@test.local",
                "API Wallet Admin"
        );
        auth.updateUserRole(admin.getId(), "ADMIN");
        admin.setRole("ADMIN");

        ApiSessionService sessions = new ApiSessionService();
        String bidderToken = sessions.createSession(bidder).token();
        String adminToken = sessions.createSession(admin).token();
        AuctionApiHandler handler = new AuctionApiHandler(
                auth,
                AuctionWorkflowService.getInstance(),
                sessions,
                new AuctionRealtimeBroker()
        );

        assertJsonStatus(handler, "PATCH", "/api/users/me/profile", ApiJson.stringify(Map.of(
                "fullName", "API Wallet Bidder Updated",
                "phoneNumber", "555-0199",
                "address", "Updated Address"
        )), bidderToken, 200);
        assertJsonStatus(handler, "PATCH", "/api/users/me/avatar",
                ApiJson.stringify(Map.of("avatarUrl", "https://example.test/avatar.png")), bidderToken, 200);
        assertJsonStatus(handler, "POST", "/api/users/me/wallet/pin",
                ApiJson.stringify(Map.of("newPin", "2468")), bidderToken, 200);
        assertJsonStatus(handler, "POST", "/api/users/me/wallet",
                ApiJson.stringify(Map.of("walletPin", "2468")), bidderToken, 200);
        assertJsonStatus(handler, "POST", "/api/users/me/wallet/authorization",
                ApiJson.stringify(Map.of("walletPin", "2468")), bidderToken, 201);
        assertJsonStatus(handler, "POST", "/api/users/me/wallet/recovery", "", bidderToken, 202);

        @SuppressWarnings("unchecked")
        Map<String, String> recoveryCodes = (Map<String, String>) privateField(
                WalletService.getInstance(),
                "recoveryCodesByUserId"
        );
        String recoveryCode = "999999";
        recoveryCodes.put(bidder.getId(), CredentialHasher.hash(recoveryCode));
        assertJsonStatus(handler, "POST", "/api/users/me/wallet/pin/reset", ApiJson.stringify(Map.of(
                "recoveryCode", recoveryCode,
                "newPin", "1357"
        )), bidderToken, 200);

        assertJsonStatus(handler, "POST", "/api/users/me/wallet/accounts", ApiJson.stringify(Map.of(
                "accountName", "API Wallet Bidder Updated",
                "providerName", "API Bank",
                "accountReference", "123456789012",
                "initialBalance", 25.0,
                "primary", false,
                "walletPin", "1357"
        )), bidderToken, 201);
        String accountId = dashboard.getWallet(bidder, "1357").linkedAccounts().get(0).id();
        assertJsonStatus(handler, "POST", "/api/users/me/wallet/accounts/" + accountId + "/primary",
                ApiJson.stringify(Map.of("walletPin", "1357")), bidderToken, 200);
        assertJsonStatus(handler, "POST", "/api/users/me/wallet/accounts/" + accountId + "/top-up",
                ApiJson.stringify(Map.of("amount", 10.0, "walletPin", "1357")), bidderToken, 200);
        assertJsonStatus(handler, "POST", "/api/users/me/wallet/top-up",
                ApiJson.stringify(Map.of("accountId", accountId, "amount", 5.0, "walletPin", "1357")), bidderToken, 200);
        assertJsonStatus(handler, "POST", "/api/users/me/wallet/withdraw",
                ApiJson.stringify(Map.of("accountId", accountId, "amount", 5.0, "walletPin", "1357")), bidderToken, 200);
        assertJsonStatus(handler, "GET", "/api/users/" + bidder.getId() + "/wallet/transactions", "", adminToken, 200);
        assertJsonStatus(handler, "PATCH", "/api/users/" + bidder.getId() + "/role",
                ApiJson.stringify(Map.of("role", "SELLER")), adminToken, 200);
    }

    private AuctionApiHandler handler() {
        return new AuctionApiHandler(
                AuthenticationService.getInstance(),
                AuctionWorkflowService.getInstance(),
                new ApiSessionService(),
                new AuctionRealtimeBroker()
        );
    }

    private FakeExchange exchange(String method, String path, String body) {
        return new FakeExchange(method, URI.create(path), body);
    }

    private FakeExchange authorizedExchange(String method, String path, String body, String token) {
        FakeExchange exchange = exchange(method, path, body);
        exchange.getRequestHeaders().set("Authorization", "Bearer " + token);
        return exchange;
    }

    private void assertJsonStatus(
            AuctionApiHandler handler,
            String method,
            String path,
            String body,
            String token,
            int expectedStatus
    ) throws Exception {
        FakeExchange exchange = authorizedExchange(method, path, body, token);

        handler.handle(exchange);

        assertEquals(expectedStatus, exchange.statusCode, exchange.responseBody());
    }

    private void assertJsonError(
            AuctionApiHandler handler,
            String method,
            String path,
            String body,
            String token,
            int expectedStatus,
            String expectedMessage
    ) throws Exception {
        FakeExchange exchange = exchange(method, path, body);
        exchange.getRequestHeaders().set("Authorization", "Bearer " + token);

        handler.handle(exchange);

        assertEquals(expectedStatus, exchange.statusCode);
        assertEquals(expectedMessage, ApiJson.parseObject(exchange.responseBody()).get("error"));
    }

    private static Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static Object privateField(Object target, String name) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static final class FakeExchange extends HttpExchange {
        private final String requestMethod;
        private final URI requestUri;
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final ByteArrayInputStream requestBody;
        private OutputStream responseBody = new ByteArrayOutputStream();
        private InetSocketAddress remoteAddress = new InetSocketAddress("127.0.0.1", 50123);
        private int statusCode;
        private long responseLength;
        private boolean closed;
        private boolean failRequestUri;

        private FakeExchange(String requestMethod, URI requestUri, String requestBody) {
            this.requestMethod = requestMethod;
            this.requestUri = requestUri;
            this.requestBody = new ByteArrayInputStream(requestBody.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public Headers getRequestHeaders() {
            return requestHeaders;
        }

        @Override
        public Headers getResponseHeaders() {
            return responseHeaders;
        }

        @Override
        public URI getRequestURI() {
            if (failRequestUri) {
                throw new RuntimeException("boom");
            }
            return requestUri;
        }

        @Override
        public String getRequestMethod() {
            return requestMethod;
        }

        @Override
        public HttpContext getHttpContext() {
            return null;
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public InputStream getRequestBody() {
            return requestBody;
        }

        @Override
        public OutputStream getResponseBody() {
            return responseBody;
        }

        @Override
        public void sendResponseHeaders(int responseCode, long responseLength) {
            this.statusCode = responseCode;
            this.responseLength = responseLength;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return remoteAddress;
        }

        @Override
        public int getResponseCode() {
            return statusCode;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return new InetSocketAddress("127.0.0.1", 8080);
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public void setAttribute(String name, Object value) {
        }

        @Override
        public void setStreams(InputStream inputStream, OutputStream outputStream) {
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }

        private String responseBody() {
            return responseBody instanceof ByteArrayOutputStream bytes
                    ? bytes.toString(StandardCharsets.UTF_8)
                    : "";
        }
    }
}
