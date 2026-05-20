package org.example.server;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.example.auction.AuctionDepositResult;
import org.example.auction.AuctionSettlement;
import org.example.auction.AuctionSummary;
import org.example.auction.BidValidationResult;
import org.example.exception.InvalidPasswordException;
import org.example.exception.UserNotFound;
import org.example.model.ApprovalStatus;
import org.example.model.Item;
import org.example.model.User;
import org.example.service.AuthenticationService;
import org.example.service.AuctionWorkflowService;
import org.example.service.MarketplaceDashboardService;
import org.example.viewmodel.AuctionListEntry;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AuctionApiHandler implements HttpHandler {
    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final AuthenticationService authenticationService;
    private final AuctionWorkflowService workflowService;
    private final MarketplaceDashboardService dashboardService;
    private final ApiPayloadFactory payloads;
    private final ApiSessionService sessionService;
    private final AuctionRealtimeBroker realtimeBroker;

    public AuctionApiHandler(
            AuthenticationService authenticationService,
            AuctionWorkflowService workflowService,
            ApiSessionService sessionService,
            AuctionRealtimeBroker realtimeBroker
    ) {
        this.authenticationService = authenticationService;
        this.workflowService = workflowService;
        this.dashboardService = MarketplaceDashboardService.getInstance();
        this.payloads = new ApiPayloadFactory(workflowService, dashboardService);
        this.sessionService = sessionService;
        this.realtimeBroker = realtimeBroker;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange.getResponseHeaders());

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        try {
            route(exchange);
        } catch (ApiHttpException e) {
            sendJson(exchange, e.statusCode(), jsonObject(
                    "error", e.getMessage(),
                    "status", e.statusCode(),
                    "timestamp", formatDateTime(LocalDateTime.now())
            ));
        } catch (ApiResourceNotFoundException e) {
            sendJson(exchange, 404, jsonObject(
                    "error", e.getMessage(),
                    "status", 404,
                    "timestamp", formatDateTime(LocalDateTime.now())
            ));
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, jsonObject(
                    "error", e.getMessage(),
                    "status", 400,
                    "timestamp", formatDateTime(LocalDateTime.now())
            ));
        } catch (IllegalStateException e) {
            sendJson(exchange, 409, jsonObject(
                    "error", e.getMessage(),
                    "status", 409,
                    "timestamp", formatDateTime(LocalDateTime.now())
            ));
        } catch (Exception e) {
            sendJson(exchange, 500, jsonObject(
                    "error", "Internal server error.",
                    "detail", e.getMessage(),
                    "status", 500,
                    "timestamp", formatDateTime(LocalDateTime.now())
            ));
        } finally {
            sessionService.expireSessions();
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        List<String> segments = pathSegments(exchange);
        if (segments.isEmpty()) {
            requireMethod(exchange, "GET");
            sendJson(exchange, 200, jsonObject(
                    "name", "Online Auction API",
                    "serverTime", formatDateTime(LocalDateTime.now()),
                    "endpoints", List.of(
                            "/api/health",
                            "/api/auth/login",
                            "/api/auth/register",
                            "/api/auth/me",
                            "/api/auth/logout",
                            "/api/users/me/profile",
                            "/api/users/me/wallet",
                            "/api/users/me/wallet/authorization",
                            "/api/users/me/wallet/pin",
                            "/api/users/me/wallet/recovery",
                            "/api/users/me/wallet/pin/reset",
                            "/api/users/me/wallet/accounts",
                            "/api/users/me/wallet/accounts/{id}/primary",
                            "/api/users/me/wallet/accounts/{id}",
                            "/api/users/me/wallet/top-up",
                            "/api/users/me/wallet/withdraw",
                            "/api/users/{id}/wallet/transactions",
                            "/api/auctions",
                            "/api/auctions/{id}",
                            "/api/auctions/{id}/entry-deposit",
                            "/api/auctions/{id}/start",
                            "/api/auctions/{id}/finish",
                            "/api/auctions/{id}/bids",
                            "/api/auctions/{id}/auto-bid",
                            "/api/settlements",
                            "/api/notifications",
                            "/api/items",
                            "/api/items/pending",
                            "/api/items/{id}/approval",
                            "/api/events/stream?token={token}"
                    )
            ));
            return;
        }

        String rootSegment = segments.get(0);
        switch (rootSegment) {
            case "health" -> handleHealth(exchange);
            case "auth" -> handleAuth(exchange, segments);
            case "users" -> handleUsers(exchange, segments);
            case "auctions" -> handleAuctions(exchange, segments);
            case "settlements" -> handleSettlements(exchange, segments);
            case "notifications" -> handleNotifications(exchange, segments);
            case "items" -> handleItems(exchange, segments);
            case "events" -> handleEvents(exchange, segments);
            default -> throw new ApiHttpException(404, "Unknown API route.");
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        requireMethod(exchange, "GET");
        sendJson(exchange, 200, jsonObject(
                "status", "ok",
                "serverTime", formatDateTime(LocalDateTime.now())
        ));
    }

    private void handleAuth(HttpExchange exchange, List<String> segments) throws IOException {
        if (segments.size() == 2 && "login".equals(segments.get(1))) {
            requireMethod(exchange, "POST");
            Map<String, Object> request = ApiJson.parseObject(readRequestBody(exchange));
            String username = ApiJson.requireString(request, "username");
            String password = ApiJson.requireString(request, "password");

            try {
                User user = authenticationService.loginOrThrow(username, password);
                ApiSessionService.SessionState session = sessionService.createSession(user);
                sendJson(exchange, 200, jsonObject(
                        "token", session.token(),
                        "createdAt", session.createdAt().toString(),
                        "expiresAt", session.expiresAt().toString(),
                        "user", payloads.user(user)
                ));
                return;
            } catch (UserNotFound | InvalidPasswordException e) {
                throw new ApiHttpException(401, e.getMessage());
            }
        }

        if (segments.size() == 2 && "register".equals(segments.get(1))) {
            requireMethod(exchange, "POST");
            Map<String, Object> request = ApiJson.parseObject(readRequestBody(exchange));
            String username = ApiJson.requireString(request, "username");
            String password = ApiJson.requireString(request, "password");
            String email = ApiJson.requireString(request, "email");
            String fullName = optionalString(request, "fullName");
            String role = optionalString(request, "role");

            try {
                User user = "SELLER".equalsIgnoreCase(role)
                        ? authenticationService.registerManualSeller(username, password, email, fullName)
                        : authenticationService.registerManualBidder(username, password, email, fullName);
                ApiSessionService.SessionState session = sessionService.createSession(user);
                sendJson(exchange, 201, payloads.auth(session));
                return;
            } catch (IllegalArgumentException e) {
                throw new ApiHttpException(409, e.getMessage());
            }
        }

        if (segments.size() == 2 && "me".equals(segments.get(1))) {
            requireMethod(exchange, "GET");
            User user = requireAuthenticatedUser(exchange, false);
            sendJson(exchange, 200, jsonObject(
                    "user", payloads.user(user)
            ));
            return;
        }

        if (segments.size() == 2 && "logout".equals(segments.get(1))) {
            requireMethod(exchange, "POST");
            String token = requireToken(exchange, false);
            sessionService.revoke(token);
            sendJson(exchange, 200, jsonObject(
                    "message", "Session revoked."
            ));
            return;
        }

        throw new ApiHttpException(404, "Unknown authentication route.");
    }

    private void handleUsers(HttpExchange exchange, List<String> segments) throws IOException {
        User currentUser = requireAuthenticatedUser(exchange, false);

        if (segments.size() == 1) {
            requireMethod(exchange, "GET");
            requireAdmin(currentUser);

            List<Map<String, Object>> users = new ArrayList<>();
            for (User user : dashboardService.getAllUsers()) {
                users.add(payloads.user(user));
            }
            sendJson(exchange, 200, jsonObject(
                    "count", users.size(),
                    "users", users
            ));
            return;
        }

        if (segments.size() == 3 && "me".equals(segments.get(1)) && "profile".equals(segments.get(2))) {
            requireWriteMethod(exchange);
            Map<String, Object> request = ApiJson.parseObject(readRequestBody(exchange));
            dashboardService.updateProfile(
                    currentUser,
                    optionalString(request, "fullName"),
                    optionalString(request, "phoneNumber"),
                    optionalString(request, "address")
            );
            sendJson(exchange, 200, jsonObject(
                    "message", "Profile saved.",
                    "user", payloads.user(currentUser)
            ));
            return;
        }

        if (segments.size() == 3 && "me".equals(segments.get(1)) && "avatar".equals(segments.get(2))) {
            requireWriteMethod(exchange);
            Map<String, Object> request = ApiJson.parseObject(readRequestBody(exchange));
            dashboardService.updateAvatar(currentUser, optionalString(request, "avatarUrl"));
            sendJson(exchange, 200, jsonObject(
                    "message", "Avatar saved.",
                    "user", payloads.user(currentUser)
            ));
            return;
        }

        if (segments.size() == 3 && "me".equals(segments.get(1)) && "wallet".equals(segments.get(2))) {
            requireMethod(exchange, "POST");
            Map<String, Object> request = ApiJson.parseObject(readRequestBody(exchange));
            sendJson(exchange, 200, jsonObject(
                    "wallet", payloads.wallet(dashboardService.getWallet(currentUser, ApiJson.requireString(request, "walletPin"))),
                    "user", payloads.user(currentUser)
            ));
            return;
        }

        if (segments.size() == 4 && "me".equals(segments.get(1)) && "wallet".equals(segments.get(2)) && "pin".equals(segments.get(3))) {
            requireWriteMethod(exchange);
            Map<String, Object> request = ApiJson.parseObject(readRequestBody(exchange));
            dashboardService.setWalletPin(currentUser, ApiJson.requireString(request, "newPin"));
            sendJson(exchange, 200, jsonObject(
                    "message", "Wallet PIN saved.",
                    "wallet", payloads.wallet(dashboardService.getWallet(currentUser, ApiJson.requireString(request, "newPin"))),
                    "user", payloads.user(currentUser)
            ));
            return;
        }

        if (segments.size() == 4 && "me".equals(segments.get(1)) && "wallet".equals(segments.get(2)) && "authorization".equals(segments.get(3))) {
            requireMethod(exchange, "POST");
            Map<String, Object> request = ApiJson.parseObject(readRequestBody(exchange));
            sendJson(exchange, 201, jsonObject(
                    "authorization", payloads.walletAuthorization(dashboardService.authorizeWallet(
                            currentUser,
                            ApiJson.requireString(request, "walletPin"),
                            java.time.Duration.ofMinutes(120)
                    ))
            ));
            return;
        }

        if (segments.size() == 4 && "me".equals(segments.get(1)) && "wallet".equals(segments.get(2)) && "recovery".equals(segments.get(3))) {
            requireMethod(exchange, "POST");
            sendJson(exchange, 202, jsonObject(
                    "recovery", payloads.walletRecovery(dashboardService.requestWalletPinRecovery(currentUser))
            ));
            return;
        }

        if (segments.size() == 5
                && "me".equals(segments.get(1))
                && "wallet".equals(segments.get(2))
                && "pin".equals(segments.get(3))
                && "reset".equals(segments.get(4))) {
            requireMethod(exchange, "POST");
            Map<String, Object> request = ApiJson.parseObject(readRequestBody(exchange));
            dashboardService.resetWalletPin(
                    currentUser,
                    ApiJson.requireString(request, "recoveryCode"),
                    ApiJson.requireString(request, "newPin")
            );
            sendJson(exchange, 200, jsonObject(
                    "message", "Wallet PIN reset.",
                    "wallet", payloads.wallet(dashboardService.getWallet(currentUser, ApiJson.requireString(request, "newPin"))),
                    "user", payloads.user(currentUser)
            ));
            return;
        }

        if (segments.size() == 4 && "me".equals(segments.get(1)) && "wallet".equals(segments.get(2)) && "accounts".equals(segments.get(3))) {
            requireMethod(exchange, "POST");
            Map<String, Object> request = ApiJson.parseObject(readRequestBody(exchange));
            sendJson(exchange, 201, jsonObject(
                    "message", "Wallet account saved.",
                    "wallet", payloads.wallet(dashboardService.addWalletAccount(
                            currentUser,
                            ApiJson.requireString(request, "accountName"),
                            ApiJson.requireString(request, "providerName"),
                            ApiJson.requireString(request, "accountReference"),
                            0.0,
                            optionalBoolean(request, "primary"),
                            ApiJson.requireString(request, "walletPin")
                    )),
                    "user", payloads.user(currentUser)
            ));
            return;
        }

        if (segments.size() == 6
                && "me".equals(segments.get(1))
                && "wallet".equals(segments.get(2))
                && "accounts".equals(segments.get(3))
                && "primary".equals(segments.get(5))) {
            requireMethod(exchange, "POST");
            Map<String, Object> request = ApiJson.parseObject(readRequestBody(exchange));
            sendJson(exchange, 200, jsonObject(
                    "message", "Primary wallet account saved.",
                    "wallet", payloads.wallet(dashboardService.setPrimaryWalletAccount(
                            currentUser,
                            segments.get(4),
                            ApiJson.requireString(request, "walletPin")
                    )),
                    "user", payloads.user(currentUser)
            ));
            return;
        }

        if (segments.size() == 5
                && "me".equals(segments.get(1))
                && "wallet".equals(segments.get(2))
                && "accounts".equals(segments.get(3))) {
            requireMethod(exchange, "DELETE");
            Map<String, Object> request = ApiJson.parseObject(readRequestBody(exchange));
            sendJson(exchange, 200, jsonObject(
                    "message", "Wallet account removed.",
                    "wallet", payloads.wallet(dashboardService.removeWalletAccount(
                            currentUser,
                            segments.get(4),
                            ApiJson.requireString(request, "walletPin")
                    )),
                    "user", payloads.user(currentUser)
            ));
            return;
        }

        if (segments.size() == 4 && "me".equals(segments.get(1)) && "wallet".equals(segments.get(2)) && "top-up".equals(segments.get(3))) {
            requireMethod(exchange, "POST");
            Map<String, Object> request = ApiJson.parseObject(readRequestBody(exchange));
            sendJson(exchange, 200, jsonObject(
                    "message", "Wallet money received.",
                    "wallet", payloads.wallet(dashboardService.receiveWalletMoney(
                            currentUser,
                            optionalString(request, "accountId"),
                            ApiJson.requireDouble(request, "amount"),
                            ApiJson.requireString(request, "walletPin")
                    )),
                    "user", payloads.user(currentUser)
            ));
            return;
        }

        if (segments.size() == 4 && "me".equals(segments.get(1)) && "wallet".equals(segments.get(2)) && "withdraw".equals(segments.get(3))) {
            requireMethod(exchange, "POST");
            Map<String, Object> request = ApiJson.parseObject(readRequestBody(exchange));
            sendJson(exchange, 200, jsonObject(
                    "message", "Wallet money sent.",
                    "wallet", payloads.wallet(dashboardService.sendWalletMoney(
                            currentUser,
                            optionalString(request, "accountId"),
                            ApiJson.requireDouble(request, "amount"),
                            ApiJson.requireString(request, "walletPin")
                    )),
                    "user", payloads.user(currentUser)
            ));
            return;
        }

        if (segments.size() == 3 && "role".equals(segments.get(2))) {
            requireWriteMethod(exchange);
            requireAdmin(currentUser);
            String userId = segments.get(1);
            Map<String, Object> request = ApiJson.parseObject(readRequestBody(exchange));
            String role = ApiJson.requireString(request, "role");
            if (!dashboardService.updateUserRole(userId, role)) {
                throw new ApiHttpException(404, "User not found: " + userId);
            }
            User updatedUser = dashboardService.findUserById(userId).orElse(null);
            sessionService.replaceUser(updatedUser);
            sendJson(exchange, 200, jsonObject(
                    "message", "Role saved.",
                    "user", updatedUser == null ? null : payloads.user(updatedUser)
            ));
            return;
        }

        if (segments.size() == 4 && "wallet".equals(segments.get(2)) && "transactions".equals(segments.get(3))) {
            requireMethod(exchange, "GET");
            requireAdmin(currentUser);
            List<Map<String, Object>> transactions = new ArrayList<>();
            for (var transaction : dashboardService.getWalletAuditTransactions(currentUser, segments.get(1))) {
                transactions.add(payloads.walletTransaction(transaction));
            }
            sendJson(exchange, 200, jsonObject(
                    "count", transactions.size(),
                    "transactions", transactions
            ));
            return;
        }

        throw new ApiHttpException(404, "Unknown user route.");
    }

    private void handleAuctions(HttpExchange exchange, List<String> segments) throws IOException {
        User authenticatedUser = requireAuthenticatedUser(exchange, false);

        if (segments.size() == 1) {
            requireMethod(exchange, "GET");
            List<Map<String, Object>> auctions = new ArrayList<>();
            for (AuctionListEntry entry : workflowService.getAuctionListEntries()) {
                auctions.add(payloads.auction(entry.getItemId(), authenticatedUser));
            }

            sendJson(exchange, 200, jsonObject(
                    "count", auctions.size(),
                    "auctions", auctions,
                    "serverTime", formatDateTime(LocalDateTime.now())
            ));
            return;
        }

        String itemId = segments.get(1);
        if (segments.size() == 2) {
            requireMethod(exchange, "GET");
            sendJson(exchange, 200, jsonObject(
                    "auction", payloads.auction(itemId, authenticatedUser)
            ));
            return;
        }

        if (segments.size() == 3 && "entry-deposit".equals(segments.get(2))) {
            requireMethod(exchange, "POST");
            Map<String, Object> request = ApiJson.parseObject(readRequestBody(exchange));
            AuctionDepositResult result = dashboardService.confirmAuctionEntry(
                    itemId,
                    authenticatedUser,
                    ApiJson.requireString(request, "walletPin")
            );
            sendJson(exchange, result.accepted() ? 201 : 409, jsonObject(
                    "accepted", result.accepted(),
                    "message", result.message(),
                    "requiredDeposit", result.requiredDeposit(),
                    "lockedDeposit", result.lockedDeposit(),
                    "status", result.status() == null ? null : result.status().name(),
                    "user", payloads.user(authenticatedUser),
                    "auction", payloads.auction(itemId, authenticatedUser)
            ));
            return;
        }

        if (segments.size() == 3 && "start".equals(segments.get(2))) {
            requireMethod(exchange, "POST");
            if (!dashboardService.startAuction(authenticatedUser, itemId)) {
                throw new ApiHttpException(409, "Auction could not be started.");
            }
            realtimeBroker.publish("auction-updated", jsonObject(
                    "type", "AUCTION_STARTED",
                    "auction", payloads.auction(itemId),
                    "serverTime", formatDateTime(LocalDateTime.now())
            ));
            sendJson(exchange, 200, jsonObject(
                    "message", "Auction started.",
                    "auction", payloads.auction(itemId, authenticatedUser)
            ));
            return;
        }

        if (segments.size() == 3 && "finish".equals(segments.get(2))) {
            requireMethod(exchange, "POST");
            if (!dashboardService.finishAuction(authenticatedUser, itemId)) {
                throw new ApiHttpException(409, "Auction could not be finished.");
            }
            realtimeBroker.publish("auction-updated", jsonObject(
                    "type", "AUCTION_FINISHED",
                    "auction", payloads.auction(itemId),
                    "serverTime", formatDateTime(LocalDateTime.now())
            ));
            sendJson(exchange, 200, jsonObject(
                    "message", "Auction finished and locked.",
                    "auction", payloads.auction(itemId, authenticatedUser),
                    "settlement", dashboardService.getSettlement(itemId).map(payloads::settlement).orElse(null)
            ));
            return;
        }

        if (segments.size() == 3 && "settlement".equals(segments.get(2))) {
            requireMethod(exchange, "GET");
            sendJson(exchange, 200, jsonObject(
                    "settlement", dashboardService.getSettlement(itemId).map(payloads::settlement).orElse(null)
            ));
            return;
        }

        if (segments.size() == 4 && "settlement".equals(segments.get(2))) {
            requireMethod(exchange, "POST");
            Map<String, Object> request = ApiJson.parseObject(readRequestBody(exchange));
            String walletPin = ApiJson.requireString(request, "walletPin");
            AuctionSettlement settlement = switch (segments.get(3)) {
                case "admit-result" -> dashboardService.admitWinnerResult(itemId, authenticatedUser, walletPin);
                case "ship" -> dashboardService.markGoodsShipped(itemId, authenticatedUser, walletPin);
                case "confirm-received" -> dashboardService.confirmGoodsReceived(itemId, authenticatedUser, walletPin);
                case "report-not-received" -> dashboardService.reportGoodsNotReceived(
                        itemId,
                        authenticatedUser,
                        optionalString(request, "reason"),
                        walletPin
                );
                case "admin-unfreeze" -> dashboardService.adminUnfreezeRemainingPayment(itemId, authenticatedUser, walletPin);
                case "admin-freeze" -> dashboardService.adminKeepRemainingPaymentFrozen(itemId, authenticatedUser, walletPin);
                default -> throw new ApiHttpException(404, "Unknown settlement action.");
            };
            realtimeBroker.publish("settlement-updated", jsonObject(
                    "type", "SETTLEMENT_UPDATED",
                    "action", segments.get(3),
                    "settlement", payloads.settlement(settlement),
                    "serverTime", formatDateTime(LocalDateTime.now())
            ));
            sendJson(exchange, 200, jsonObject(
                    "message", "Settlement updated.",
                    "settlement", payloads.settlement(settlement),
                    "user", payloads.user(authenticatedUser)
            ));
            return;
        }

        if (segments.size() == 3 && "bids".equals(segments.get(2))) {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 200, jsonObject(
                        "itemId", itemId,
                        "bids", payloads.bidHistory(itemId)
                ));
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, Object> request = ApiJson.parseObject(readRequestBody(exchange));
                double amount = ApiJson.requireDouble(request, "amount");
                BidValidationResult result = dashboardService.placeBidWithDeposit(
                        itemId,
                        authenticatedUser,
                        amount,
                        ApiJson.requireString(request, "walletPin")
                );
                AuctionSummary latestSummary = result.accepted() ? workflowService.getSummary(itemId) : null;

                Map<String, Object> response = jsonObject(
                        "accepted", result.accepted(),
                        "message", result.message(),
                        "attemptedAmount", result.attemptedAmount(),
                        "currentPrice", latestSummary == null ? result.currentPrice() : latestSummary.currentPrice(),
                        "minimumAllowedBid", latestSummary == null ? result.minimumAllowedBid() : latestSummary.minimumNextBid(),
                        "status", latestSummary == null
                                ? (result.status() == null ? null : result.status().name())
                                : latestSummary.status().name(),
                        "effectiveEndTime", formatDateTime(result.effectiveEndTime()),
                        "user", payloads.user(authenticatedUser),
                        "auction", payloads.auction(itemId, authenticatedUser)
                );

                if (result.accepted()) {
                    realtimeBroker.publish("auction-updated", jsonObject(
                            "type", "BID_ACCEPTED",
                            "auction", payloads.auction(itemId),
                            "latestBid", payloads.latestBid(itemId),
                            "serverTime", formatDateTime(LocalDateTime.now())
                    ));
                    sendJson(exchange, 201, response);
                    return;
                }

                sendJson(exchange, 409, response);
                return;
            }
        }

        if (segments.size() == 3 && "auto-bid".equals(segments.get(2))) {
            requireMethod(exchange, "POST");
            Map<String, Object> request = ApiJson.parseObject(readRequestBody(exchange));
            double maxLimit = ApiJson.requireDouble(request, "maxLimit");
            double bidIncrement = request.containsKey("bidIncrement")
                    ? ApiJson.requireDouble(request, "bidIncrement")
                    : 0.0;
            if (!dashboardService.registerAutoBidWithDeposit(
                    itemId,
                    authenticatedUser,
                    maxLimit,
                    bidIncrement,
                    ApiJson.requireString(request, "walletPin")
            )) {
                throw new ApiHttpException(409, "Confirm entry deposit and make sure available balance covers the auto-bid maximum.");
            }
            sendJson(exchange, 201, jsonObject(
                "message", "Auto-bid saved.",
                "itemId", itemId,
                "maxLimit", maxLimit,
                "bidIncrement", bidIncrement,
                "user", payloads.user(authenticatedUser)
            ));
            return;
        }

        throw new ApiHttpException(404, "Unknown auction route.");
    }

    private void handleSettlements(HttpExchange exchange, List<String> segments) throws IOException {
        User currentUser = requireAuthenticatedUser(exchange, false);
        requireMethod(exchange, "GET");
        if (segments.size() != 1) {
            throw new ApiHttpException(404, "Unknown settlement route.");
        }

        List<Map<String, Object>> settlements = new ArrayList<>();
        for (AuctionSettlement settlement : dashboardService.getAllSettlements()) {
            boolean visibleToUser = "ADMIN".equalsIgnoreCase(currentUser.getRole())
                    || settlement.getSellerId().equals(currentUser.getId())
                    || settlement.getWinnerBidderId().equals(currentUser.getId());
            if (visibleToUser) {
                settlements.add(payloads.settlement(settlement));
            }
        }
        sendJson(exchange, 200, jsonObject(
                "count", settlements.size(),
                "settlements", settlements
        ));
    }

    private void handleNotifications(HttpExchange exchange, List<String> segments) throws IOException {
        User currentUser = requireAuthenticatedUser(exchange, false);
        requireMethod(exchange, "GET");
        if (segments.size() != 1) {
            throw new ApiHttpException(404, "Unknown notification route.");
        }

        List<Map<String, Object>> notifications = new ArrayList<>();
        for (var notification : dashboardService.getNotifications(currentUser)) {
            notifications.add(payloads.notification(notification));
        }
        sendJson(exchange, 200, jsonObject(
                "count", notifications.size(),
                "notifications", notifications
        ));
    }

    private void handleItems(HttpExchange exchange, List<String> segments) throws IOException {
        User currentUser = requireAuthenticatedUser(exchange, false);

        if (segments.size() == 1) {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                List<Map<String, Object>> items = new ArrayList<>();
                for (Item item : workflowService.getAllItems()) {
                    items.add(payloads.item(item));
                }
                sendJson(exchange, 200, jsonObject(
                        "count", items.size(),
                        "items", items
                ));
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                requireSellerOrAdmin(currentUser);
                Map<String, Object> request = ApiJson.parseObject(readRequestBody(exchange));
                Item item = dashboardService.addSellerItem(
                        currentUser,
                        ApiJson.requireString(request, "type"),
                        ApiJson.requireString(request, "itemName"),
                        ApiJson.requireString(request, "description"),
                        ApiJson.requireDouble(request, "startingPrice"),
                        parseDateTime(ApiJson.requireString(request, "startTime")),
                        parseDateTime(ApiJson.requireString(request, "endTime")),
                        optionalString(request, "extraText"),
                        (int) ApiJson.requireDouble(request, "extraNumber")
                );

                if (item == null) {
                    throw new ApiHttpException(500, "Item could not be saved.");
                }

                sendJson(exchange, 201, jsonObject(
                        "message", "Item submitted.",
                        "item", payloads.item(item)
                ));
                return;
            }
        }

        if (segments.size() == 2 && "pending".equals(segments.get(1))) {
            requireMethod(exchange, "GET");
            requireAdmin(currentUser);
            List<Map<String, Object>> items = new ArrayList<>();
            for (Item item : dashboardService.getPendingApprovalItems()) {
                items.add(payloads.item(item));
            }
            sendJson(exchange, 200, jsonObject(
                    "count", items.size(),
                    "items", items
            ));
            return;
        }

        if (segments.size() == 2 && "seller".equals(segments.get(1))) {
            requireMethod(exchange, "GET");
            requireSellerOrAdmin(currentUser);
            List<Map<String, Object>> items = new ArrayList<>();
            for (Item item : dashboardService.getSellerItems(currentUser)) {
                items.add(payloads.item(item));
            }
            sendJson(exchange, 200, jsonObject(
                    "count", items.size(),
                    "items", items
            ));
            return;
        }

        if (segments.size() == 3 && "approval".equals(segments.get(2))) {
            requireWriteMethod(exchange);
            requireAdmin(currentUser);
            String itemId = segments.get(1);
            Map<String, Object> request = ApiJson.parseObject(readRequestBody(exchange));
            ApprovalStatus status = ApprovalStatus.valueOf(ApiJson.requireString(request, "approvalStatus").toUpperCase());
            if (!dashboardService.updateItemApproval(itemId, status)) {
                throw new ApiHttpException(404, "Item not found: " + itemId);
            }
            sendJson(exchange, 200, jsonObject(
                    "message", "Item approval saved.",
                    "item", workflowService.findItemById(itemId).map(payloads::item).orElse(null)
            ));
            return;
        }

        throw new ApiHttpException(404, "Unknown item route.");
    }

    private void handleEvents(HttpExchange exchange, List<String> segments) throws IOException {
        if (segments.size() != 2 || !"stream".equals(segments.get(1))) {
            throw new ApiHttpException(404, "Unknown event route.");
        }

        requireMethod(exchange, "GET");
        User user = requireAuthenticatedUser(exchange, true);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/event-stream; charset=UTF-8");
        headers.set("Cache-Control", "no-cache");
        headers.set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);

        OutputStream responseBody = exchange.getResponseBody();
        AuctionRealtimeBroker.SseClient client = realtimeBroker.register(responseBody);
        try {
            client.send("connected", ApiJson.stringify(jsonObject(
                    "message", "Realtime stream established.",
                    "user", payloads.user(user),
                    "serverTime", formatDateTime(LocalDateTime.now())
            )));

            while (client.isOpen()) {
                Thread.sleep(15_000L);
                client.heartbeat();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
        } finally {
            realtimeBroker.remove(client);
            exchange.close();
        }
    }

    private User requireAuthenticatedUser(HttpExchange exchange, boolean allowQueryToken) {
        String token = requireToken(exchange, allowQueryToken);
        return sessionService.findUser(token)
                .orElseThrow(() -> new ApiHttpException(401, "Authentication required."));
    }

    private String requireToken(HttpExchange exchange, boolean allowQueryToken) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7).trim();
        }

        if (allowQueryToken) {
            String queryToken = queryParameters(exchange).get("token");
            if (queryToken != null && !queryToken.isBlank()) {
                return queryToken.trim();
            }
        }

        throw new ApiHttpException(401, "Missing bearer token.");
    }

    private void requireAdmin(User user) {
        if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new ApiHttpException(403, "Admin role required.");
        }
    }

    private void requireSellerOrAdmin(User user) {
        if (user == null
                || (!"SELLER".equalsIgnoreCase(user.getRole()) && !"ADMIN".equalsIgnoreCase(user.getRole()))) {
            throw new ApiHttpException(403, "Seller or admin role required.");
        }
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        byte[] payload = exchange.getRequestBody().readAllBytes();
        return new String(payload, StandardCharsets.UTF_8);
    }

    private List<String> pathSegments(HttpExchange exchange) {
        String rawPath = Optional.ofNullable(exchange.getRequestURI().getPath()).orElse("");
        String relativePath = rawPath.startsWith("/api") ? rawPath.substring(4) : rawPath;
        String[] tokens = relativePath.split("/");
        List<String> segments = new ArrayList<>();
        for (String token : tokens) {
            if (!token.isBlank()) {
                segments.add(token);
            }
        }
        return segments;
    }

    private Map<String, String> queryParameters(HttpExchange exchange) {
        String rawQuery = exchange.getRequestURI().getRawQuery();
        Map<String, String> parameters = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return parameters;
        }

        for (String pair : rawQuery.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            String[] tokens = pair.split("=", 2);
            String key = URLDecoder.decode(tokens[0], StandardCharsets.UTF_8);
            String value = tokens.length > 1
                    ? URLDecoder.decode(tokens[1], StandardCharsets.UTF_8)
                    : "";
            parameters.put(key, value);
        }
        return parameters;
    }

    private void requireMethod(HttpExchange exchange, String expectedMethod) {
        if (!expectedMethod.equalsIgnoreCase(exchange.getRequestMethod())) {
            throw new ApiHttpException(405, "Method not allowed.");
        }
    }

    private void requireWriteMethod(HttpExchange exchange) {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())
                && !"PATCH".equalsIgnoreCase(exchange.getRequestMethod())) {
            throw new ApiHttpException(405, "Method not allowed.");
        }
    }

    private void sendJson(HttpExchange exchange, int statusCode, Map<String, Object> payload) throws IOException {
        byte[] responseBytes = ApiJson.stringify(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream responseBody = exchange.getResponseBody()) {
            responseBody.write(responseBytes);
        } finally {
            exchange.close();
        }
    }

    private void addCorsHeaders(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        headers.set("Access-Control-Allow-Methods", "GET, POST, PATCH, DELETE, OPTIONS");
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : ISO_DATE_TIME.format(value);
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value.trim(), ISO_DATE_TIME);
    }

    private String optionalString(Map<String, Object> source, String fieldName) {
        Object value = source.get(fieldName);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean optionalBoolean(Map<String, Object> source, String fieldName) {
        Object value = source.get(fieldName);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value instanceof String text && Boolean.parseBoolean(text.trim());
    }

    private double optionalDouble(Map<String, Object> source, String fieldName) {
        Object value = source.get(fieldName);
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Double.parseDouble(text.trim());
        }
        return 0.0;
    }

    private Map<String, Object> jsonObject(Object... fields) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int index = 0; index < fields.length; index += 2) {
            payload.put(String.valueOf(fields[index]), fields[index + 1]);
        }
        return payload;
    }

    private static final class ApiHttpException extends RuntimeException {
        private final int statusCode;

        private ApiHttpException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        private int statusCode() {
            return statusCode;
        }
    }
}
