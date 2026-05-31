package org.example.client;

import org.example.auction.AuctionStatus;
import org.example.auction.AuctionDepositResult;
import org.example.auction.AuctionRules;
import org.example.auction.BidValidationResult;
import org.example.model.Admin;
import org.example.model.ApprovalStatus;
import org.example.model.Bid;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.model.ItemFactory;
import org.example.model.PasswordRecoveryResult;
import org.example.model.Seller;
import org.example.model.User;
import org.example.model.WalletAuthorization;
import org.example.model.WalletLinkedAccount;
import org.example.model.WalletRecoveryResult;
import org.example.model.WalletSummary;
import org.example.model.WalletTransaction;
import org.example.server.ApiJson;
import org.example.viewmodel.AuctionEligibilityEntry;
import org.example.viewmodel.AuctionListEntry;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AuctionApiClient {
    private static final Duration CONNECT_TIMEOUT = resolveTimeout(
            "auction.api.connectTimeoutMillis",
            "AUCTION_API_CONNECT_TIMEOUT_MILLIS",
            3_000L
    );
    private static final Duration REQUEST_TIMEOUT = resolveTimeout(
            "auction.api.requestTimeoutMillis",
            "AUCTION_API_REQUEST_TIMEOUT_MILLIS",
            5_000L
    );
    private static final AuctionApiClient INSTANCE = new AuctionApiClient();
    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    private final String baseUrl;

    private AuctionApiClient() {
        baseUrl = resolveBaseUrl();
    }

    public static AuctionApiClient getInstance() {
        return INSTANCE;
    }

    public boolean isEnabled() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    public ConnectionTestResult testConnection() {
        Map<String, Object> response = request("GET", "/health", null, null);
        String status = stringValue(response.get("status"));
        if (!"ok".equalsIgnoreCase(status)) {
            throw new ApiClientException("Auction API health check returned unexpected status: "
                    + (status.isBlank() ? "missing" : status) + ".");
        }
        return new ConnectionTestResult(baseUrl, status, stringValue(response.get("serverTime")));
    }

    public static boolean isConnectivityFailure(ApiClientException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof IOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public AuthResult login(String username, String password) {
        Map<String, Object> response = request("POST", "/auth/login", null, jsonObject(
                "username", username,
                "password", password
        ));
        return authResult(response);
    }

    public AuthResult registerManualBidder(String username, String password, String email, String fullName) {
        Map<String, Object> response = request("POST", "/auth/register", null, jsonObject(
                "username", username,
                "password", password,
                "email", email,
                "fullName", fullName,
                "role", "BIDDER"
        ));
        return authResult(response);
    }

    public AuthResult registerManualSeller(String username, String password, String email, String fullName) {
        Map<String, Object> response = request("POST", "/auth/register", null, jsonObject(
                "username", username,
                "password", password,
                "email", email,
                "fullName", fullName,
                "role", "SELLER"
        ));
        return authResult(response);
    }

    public PasswordRecoveryResult requestPasswordRecovery(String username, String email) {
        Map<String, Object> response = request("POST", "/auth/password/recovery", null, jsonObject(
                "username", username,
                "email", email
        ));
        Object recovery = response.get("recovery");
        if (!(recovery instanceof Map<?, ?> map)) {
            throw new ApiClientException("API response did not include password recovery details.");
        }
        Map<String, Object> payload = castMap(map);
        return new PasswordRecoveryResult(
                booleanValue(payload.get("accepted")),
                stringValue(payload.get("message")),
                stringValue(payload.get("email"))
        );
    }

    public String resetPassword(String username, String email, String recoveryCode, String newPassword, String confirmPassword) {
        Map<String, Object> response = request("POST", "/auth/password/reset", null, jsonObject(
                "username", username,
                "email", email,
                "recoveryCode", recoveryCode,
                "newPassword", newPassword,
                "confirmPassword", confirmPassword
        ));
        return stringValue(response.get("message"));
    }

    public void logout(String token) {
        request("POST", "/auth/logout", token, Map.of());
    }

    public User getCurrentUser(String token) {
        return getCurrentUserSnapshot(token).user();
    }

    public CurrentUserSnapshot getCurrentUserSnapshot(String token) {
        Map<String, Object> response = request("GET", "/auth/me", token, null);
        return new CurrentUserSnapshot(
                userFromResponse(response),
                walletSnapshotFromUserResponse(response)
        );
    }

    public User updateProfile(String token, String fullName, String phoneNumber, String address) {
        Map<String, Object> response = request("PATCH", "/users/me/profile", token, jsonObject(
                "fullName", fullName,
                "phoneNumber", phoneNumber,
                "address", address
        ));
        return userFromResponse(response);
    }

    public User updateAvatar(String token, String avatarUrl) {
        Map<String, Object> response = request("PATCH", "/users/me/avatar", token, jsonObject(
                "avatarUrl", avatarUrl
        ));
        return userFromResponse(response);
    }

    public WalletSummary getWallet(String token, String walletPin) {
        Map<String, Object> response = request("POST", "/users/me/wallet", token, jsonObject(
                "walletPin", walletPin
        ));
        return walletFromResponse(response);
    }

    public WalletAuthorization authorizeWallet(String token, String walletPin) {
        Map<String, Object> response = request("POST", "/users/me/wallet/authorization", token, jsonObject(
                "walletPin", walletPin
        ));
        Object authorization = response.get("authorization");
        if (!(authorization instanceof Map<?, ?> map)) {
            throw new ApiClientException("API response did not include wallet authorization.");
        }
        Map<String, Object> payload = castMap(map);
        return new WalletAuthorization(
                stringValue(payload.get("token")),
                parseDateTime(stringValue(payload.get("expiresAt")))
        );
    }

    public WalletSummary setWalletPin(String token, String newPin) {
        Map<String, Object> response = request("PATCH", "/users/me/wallet/pin", token, jsonObject(
                "newPin", newPin
        ));
        return walletFromResponse(response);
    }

    public WalletRecoveryResult requestWalletPinRecovery(String token) {
        Map<String, Object> response = request("POST", "/users/me/wallet/recovery", token, Map.of());
        Object recovery = response.get("recovery");
        if (!(recovery instanceof Map<?, ?> map)) {
            throw new ApiClientException("API response did not include wallet recovery details.");
        }
        Map<String, Object> payload = castMap(map);
        return new WalletRecoveryResult(
                booleanValue(payload.get("accepted")),
                stringValue(payload.get("message")),
                stringValue(payload.get("email"))
        );
    }

    public WalletSummary resetWalletPin(String token, String recoveryCode, String newPin) {
        Map<String, Object> response = request("POST", "/users/me/wallet/pin/reset", token, jsonObject(
                "recoveryCode", recoveryCode,
                "newPin", newPin
        ));
        return walletFromResponse(response);
    }

    public WalletSummary addWalletAccount(
            String token,
            String accountName,
            String providerName,
            String accountReference,
            boolean primary,
            String walletPin
    ) {
        return addWalletAccount(token, accountName, providerName, accountReference, 0.0, primary, walletPin);
    }

    public WalletSummary addWalletAccount(
            String token,
            String accountName,
            String providerName,
            String accountReference,
            double initialBalance,
            boolean primary,
            String walletPin
    ) {
        Map<String, Object> response = request("POST", "/users/me/wallet/accounts", token, jsonObject(
                "accountName", accountName,
                "providerName", providerName,
                "accountReference", accountReference,
                "initialBalance", initialBalance,
                "primary", primary,
                "walletPin", walletPin
        ));
        return walletFromResponse(response);
    }

    public WalletSummary setPrimaryWalletAccount(String token, String accountId, String walletPin) {
        Map<String, Object> response = request("POST", "/users/me/wallet/accounts/" + segment(accountId) + "/primary", token, jsonObject(
                "walletPin", walletPin
        ));
        return walletFromResponse(response);
    }

    public WalletSummary removeWalletAccount(String token, String accountId, String walletPin) {
        Map<String, Object> response = request("DELETE", "/users/me/wallet/accounts/" + segment(accountId), token, jsonObject(
                "walletPin", walletPin
        ));
        return walletFromResponse(response);
    }

    public WalletSummary receiveWalletMoney(String token, String accountId, double amount, String walletPin) {
        Map<String, Object> response = request("POST", "/users/me/wallet/top-up", token, jsonObject(
                "accountId", accountId,
                "amount", amount,
                "walletPin", walletPin
        ));
        return walletFromResponse(response);
    }

    public WalletSummary sendWalletMoney(String token, String accountId, double amount, String walletPin) {
        Map<String, Object> response = request("POST", "/users/me/wallet/withdraw", token, jsonObject(
                "accountId", accountId,
                "amount", amount,
                "walletPin", walletPin
        ));
        return walletFromResponse(response);
    }

    public WalletSummary topUpWalletAccount(String token, String accountId, double amount, String walletPin) {
        Map<String, Object> response = request("POST", "/users/me/wallet/accounts/" + segment(accountId) + "/top-up", token, jsonObject(
                "amount", amount,
                "walletPin", walletPin
        ));
        return walletFromResponse(response);
    }

    public User updateUserRole(String token, String userId, String role) {
        Map<String, Object> response = request("PATCH", "/users/" + segment(userId) + "/role", token, jsonObject(
                "role", role
        ));
        Object user = response.get("user");
        return user instanceof Map<?, ?> map ? buildUser(castMap(map)) : null;
    }

    public User updateAccountBanned(String token, String userId, boolean banned) {
        Map<String, Object> response = request("PATCH", "/users/" + segment(userId) + "/ban", token, jsonObject(
                "banned", banned
        ));
        Object user = response.get("user");
        return user instanceof Map<?, ?> map ? buildUser(castMap(map)) : null;
    }

    public List<User> getAllUsers(String token) {
        Map<String, Object> response = request("GET", "/users", token, null);
        return objectList(response.get("users")).stream()
                .map(this::buildUser)
                .toList();
    }

    public List<AuctionEligibilityEntry> getAuctionEligibilityEntries(String token, User user) {
        Map<String, Object> response = request("GET", "/auctions", token, null);
        double availableBalance = user instanceof Bidder bidder ? bidder.getAvailableBalance() : 0.0;
        return objectList(response.get("auctions")).stream()
                .map(auction -> buildAuctionEligibilityEntry(auction, availableBalance))
                .toList();
    }

    public List<AuctionListEntry> getAuctionListEntries(String token) {
        Map<String, Object> response = request("GET", "/auctions", token, null);
        return objectList(response.get("auctions")).stream()
                .map(this::buildAuctionListEntry)
                .toList();
    }

    public AuctionDetail getAuction(String token, String itemId) {
        Map<String, Object> response = request("GET", "/auctions/" + segment(itemId), token, null);
        Object auction = response.get("auction");
        if (!(auction instanceof Map<?, ?> map)) {
            throw new ApiClientException("API response did not include auction details.");
        }
        return buildAuctionDetail(castMap(map));
    }

    public List<Bid> getBidHistory(String token, String itemId) {
        Map<String, Object> response = request("GET", "/auctions/" + segment(itemId) + "/bids", token, null);
        return objectList(response.get("bids")).stream()
                .map(this::buildBid)
                .toList();
    }

    public Item addSellerItem(
            String token,
            String type,
            String itemName,
            String description,
            double startingPrice,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String extraText,
            int extraNumber
    ) {
        Map<String, Object> response = request("POST", "/items", token, jsonObject(
                "type", type,
                "itemName", itemName,
                "description", description,
                "startingPrice", startingPrice,
                "startTime", formatDateTime(startTime),
                "endTime", formatDateTime(endTime),
                "extraText", extraText,
                "extraNumber", extraNumber
        ));
        Object item = response.get("item");
        return item instanceof Map<?, ?> map ? buildItem(castMap(map)) : null;
    }

    public List<Item> getSellerItems(String token) {
        Map<String, Object> response = request("GET", "/items/seller", token, null);
        return objectList(response.get("items")).stream()
                .map(this::buildItem)
                .toList();
    }

    public List<Item> getPendingApprovalItems(String token) {
        Map<String, Object> response = request("GET", "/items/pending", token, null);
        return objectList(response.get("items")).stream()
                .map(this::buildItem)
                .toList();
    }

    public Item updateItemApproval(String token, String itemId, ApprovalStatus approvalStatus) {
        Map<String, Object> response = request("PATCH", "/items/" + segment(itemId) + "/approval", token, jsonObject(
                "approvalStatus", approvalStatus.name()
        ));
        Object item = response.get("item");
        return item instanceof Map<?, ?> map ? buildItem(castMap(map)) : null;
    }

    public BidValidationResult placeBid(String token, String itemId, double amount) {
        return placeBid(token, itemId, amount, null);
    }

    public BidValidationResult placeBid(String token, String itemId, double amount, String walletPin) {
        Map<String, Object> response = requestBusinessResult("POST", "/auctions/" + segment(itemId) + "/bids", token, jsonObject(
                "amount", amount,
                "walletPin", walletPin
        ));
        return buildBidResult(response);
    }

    public EntryDepositResponse confirmAuctionEntry(String token, String itemId) {
        return confirmAuctionEntry(token, itemId, null);
    }

    public EntryDepositResponse confirmAuctionEntry(String token, String itemId, String walletPin) {
        Map<String, Object> response = requestBusinessResult("POST", "/auctions/" + segment(itemId) + "/entry-deposit", token, jsonObject(
                "walletPin", walletPin
        ));
        return new EntryDepositResponse(
                buildDepositResult(response),
                userFromResponse(response)
        );
    }

    public void registerAutoBid(String token, String itemId, double maxLimit) {
        registerAutoBid(token, itemId, maxLimit, null);
    }

    public void registerAutoBid(String token, String itemId, double maxLimit, String walletPin) {
        registerAutoBid(token, itemId, maxLimit, 0.0, walletPin);
    }

    public void registerAutoBid(String token, String itemId, double maxLimit, double bidIncrement, String walletPin) {
        request("POST", "/auctions/" + segment(itemId) + "/auto-bid", token, jsonObject(
                "maxLimit", maxLimit,
                "bidIncrement", bidIncrement,
                "walletPin", walletPin
        ));
    }

    public boolean disableAutoBid(String token, String itemId, String walletPin) {
        Map<String, Object> response = request("DELETE", "/auctions/" + segment(itemId) + "/auto-bid", token, jsonObject(
                "walletPin", walletPin
        ));
        return booleanValue(response.get("disabled"));
    }

    public void startAuction(String token, String itemId) {
        request("POST", "/auctions/" + segment(itemId) + "/start", token, Map.of());
    }

    public void finishAuction(String token, String itemId) {
        request("POST", "/auctions/" + segment(itemId) + "/finish", token, Map.of());
    }

    public SettlementDetail getSettlement(String token, String itemId) {
        Map<String, Object> response = request("GET", "/auctions/" + segment(itemId) + "/settlement", token, null);
        Object settlement = response.get("settlement");
        return settlement instanceof Map<?, ?> map ? buildSettlementDetail(castMap(map)) : null;
    }

    public SettlementDetail admitWinnerResult(String token, String itemId) {
        return admitWinnerResult(token, itemId, null);
    }

    public SettlementDetail admitWinnerResult(String token, String itemId, String walletPin) {
        Map<String, Object> response = request("POST", "/auctions/" + segment(itemId) + "/settlement/admit-result", token, jsonObject(
                "walletPin", walletPin
        ));
        return buildSettlementFromResponse(response);
    }

    public SettlementDetail markGoodsShipped(String token, String itemId) {
        return markGoodsShipped(token, itemId, null);
    }

    public SettlementDetail markGoodsShipped(String token, String itemId, String walletPin) {
        Map<String, Object> response = request("POST", "/auctions/" + segment(itemId) + "/settlement/ship", token, jsonObject(
                "walletPin", walletPin
        ));
        return buildSettlementFromResponse(response);
    }

    public SettlementDetail confirmGoodsReceived(String token, String itemId) {
        return confirmGoodsReceived(token, itemId, null);
    }

    public SettlementDetail confirmGoodsReceived(String token, String itemId, String walletPin) {
        Map<String, Object> response = request("POST", "/auctions/" + segment(itemId) + "/settlement/confirm-received", token, jsonObject(
                "walletPin", walletPin
        ));
        return buildSettlementFromResponse(response);
    }

    public SettlementDetail reportGoodsNotReceived(String token, String itemId, String reason) {
        return reportGoodsNotReceived(token, itemId, reason, null);
    }

    public SettlementDetail reportGoodsNotReceived(String token, String itemId, String reason, String walletPin) {
        Map<String, Object> response = request("POST", "/auctions/" + segment(itemId) + "/settlement/report-not-received", token, jsonObject(
                "reason", reason,
                "walletPin", walletPin
        ));
        return buildSettlementFromResponse(response);
    }

    public SettlementDetail adminUnfreezePayment(String token, String itemId) {
        return adminUnfreezePayment(token, itemId, null);
    }

    public SettlementDetail adminUnfreezePayment(String token, String itemId, String walletPin) {
        Map<String, Object> response = request("POST", "/auctions/" + segment(itemId) + "/settlement/admin-unfreeze", token, jsonObject(
                "walletPin", walletPin
        ));
        return buildSettlementFromResponse(response);
    }

    public SettlementDetail adminKeepPaymentFrozen(String token, String itemId) {
        return adminKeepPaymentFrozen(token, itemId, null);
    }

    public SettlementDetail adminKeepPaymentFrozen(String token, String itemId, String walletPin) {
        Map<String, Object> response = request("POST", "/auctions/" + segment(itemId) + "/settlement/admin-freeze", token, jsonObject(
                "walletPin", walletPin
        ));
        return buildSettlementFromResponse(response);
    }

    public List<SettlementDetail> getSettlements(String token) {
        Map<String, Object> response = request("GET", "/settlements", token, null);
        return objectList(response.get("settlements")).stream()
                .map(this::buildSettlementDetail)
                .toList();
    }

    public List<String> getNotifications(String token) {
        return getNotificationDetails(token).stream()
                .map(NotificationDetail::displayText)
                .toList();
    }

    public List<NotificationDetail> getNotificationDetails(String token) {
        Map<String, Object> response = request("GET", "/notifications", token, null);
        return objectList(response.get("notifications")).stream()
                .map(this::buildNotificationDetail)
                .toList();
    }

    public List<WalletTransaction> getWalletAuditTransactions(String token, String userId) {
        Map<String, Object> response = request("GET", "/users/" + segment(userId) + "/wallet/transactions", token, null);
        return objectList(response.get("transactions")).stream()
                .map(this::buildWalletTransaction)
                .toList();
    }

    private Map<String, Object> request(String method, String path, String token, Map<String, Object> body) {
        return request(method, path, token, body, false);
    }

    private Map<String, Object> requestBusinessResult(String method, String path, String token, Map<String, Object> body) {
        return request(method, path, token, body, true);
    }

    private Map<String, Object> request(String method, String path, String token, Map<String, Object> body, boolean allowConflictResult) {
        if (!isEnabled()) {
            throw new ApiClientException("Auction API client is not configured.");
        }

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json");
            if (token != null && !token.isBlank()) {
                builder.header("Authorization", "Bearer " + token.trim());
            }

            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.method(method, HttpRequest.BodyPublishers.ofString(ApiJson.stringify(body)));
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            Map<String, Object> parsed = parseResponseBody(response);
            if (response.statusCode() >= 400 && !(allowConflictResult && response.statusCode() == 409)) {
                throw createFailure(response.statusCode(), parsed, null);
            }
            return parsed;
        } catch (IllegalArgumentException e) {
            throw new ApiClientException("Auction API base URL is invalid.", e);
        } catch (IOException e) {
            throw new ApiClientException("Could not reach auction API server: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiClientException("Auction API request was interrupted.", e);
        }
    }

    private Map<String, Object> parseResponseBody(HttpResponse<String> response) {
        String responseBody = response.body() == null ? "" : response.body().trim();
        if (responseBody.isEmpty()) {
            return Map.of();
        }
        try {
            return ApiJson.parseObject(responseBody);
        } catch (IllegalArgumentException e) {
            if (response.statusCode() >= 400) {
                return Map.of("message", responseBody);
            }
            throw createFailure(
                    response.statusCode(),
                    Map.of(),
                    new IllegalStateException("API returned invalid JSON.", e)
            );
        }
    }

    private ApiClientException createFailure(int statusCode, Map<String, Object> parsed, Exception cause) {
        Object error = parsed.get("error");
        if (error == null) {
            error = parsed.get("message");
        }

        String message = error == null || String.valueOf(error).isBlank()
                ? "API request failed with status " + statusCode + "."
                : String.valueOf(error).trim();
        if (message.length() > 240) {
            message = message.substring(0, 237) + "...";
        }

        if (cause == null) {
            return new ApiClientException(message, statusCode);
        }
        return new ApiClientException(message, cause, statusCode);
    }

    private AuthResult authResult(Map<String, Object> response) {
        return new AuthResult(
                stringValue(response.get("token")),
                userFromResponse(response)
        );
    }

    private User userFromResponse(Map<String, Object> response) {
        Object user = response.get("user");
        if (!(user instanceof Map<?, ?> map)) {
            throw new ApiClientException("API response did not include a user.");
        }
        return buildUser(castMap(map));
    }

    private WalletSummary walletSnapshotFromUserResponse(Map<String, Object> response) {
        Object user = response.get("user");
        if (!(user instanceof Map<?, ?> userMap)) {
            return null;
        }
        Object wallet = userMap.get("wallet");
        return wallet instanceof Map<?, ?> walletMap ? buildWalletSummary(castMap(walletMap)) : null;
    }

    private WalletSummary walletFromResponse(Map<String, Object> response) {
        Object wallet = response.get("wallet");
        if (!(wallet instanceof Map<?, ?> map)) {
            throw new ApiClientException("API response did not include wallet details.");
        }
        return buildWalletSummary(castMap(map));
    }

    private User buildUser(Map<String, Object> payload) {
        String role = stringValue(payload.get("role")).toUpperCase();
        String id = stringValue(payload.get("id"));
        String username = stringValue(payload.get("username"));
        String email = stringValue(payload.get("email"));
        double balance = doubleValue(payload.get("balance"));

        User user = switch (role) {
            case "ADMIN" -> new Admin(id, username, "", email);
            case "SELLER" -> new Seller(id, username, "", email);
            default -> new Bidder(id, username, "", email, balance);
        };
        user.setRole(role.isBlank() ? "BIDDER" : role);
        user.setFullName(stringValue(payload.get("fullName")));
        user.setPhoneNumber(stringValue(payload.get("phoneNumber")));
        user.setAddress(stringValue(payload.get("address")));
        user.setAvatarUrl(stringValue(payload.get("avatarUrl")));
        user.setAccountBanned(booleanValue(payload.get("accountBanned")));
        if (user instanceof Bidder bidder) {
            bidder.replaceLockedDeposits(lockedDeposits(payload.get("lockedDeposits")));
        }
        return user;
    }

    private WalletSummary buildWalletSummary(Map<String, Object> payload) {
        return new WalletSummary(
                stringValue(payload.get("userId")),
                doubleValue(payload.get("balance")),
                doubleValue(payload.get("lockedBalance")),
                doubleValue(payload.get("availableBalance")),
                booleanValue(payload.get("pinSet")),
                objectList(payload.get("linkedAccounts")).stream()
                        .map(this::buildWalletLinkedAccount)
                        .toList(),
                objectList(payload.get("transactions")).stream()
                        .map(this::buildWalletTransaction)
                        .toList()
        );
    }

    private WalletLinkedAccount buildWalletLinkedAccount(Map<String, Object> payload) {
        return new WalletLinkedAccount(
                stringValue(payload.get("id")),
                stringValue(payload.get("userId")),
                stringValue(payload.get("accountName")),
                stringValue(payload.get("providerName")),
                stringValue(payload.get("accountReference")),
                doubleValue(payload.get("balance")),
                booleanValue(payload.get("primary")),
                parseDateTime(stringValue(payload.get("createdAt")))
        );
    }

    private WalletTransaction buildWalletTransaction(Map<String, Object> payload) {
        return new WalletTransaction(
                stringValue(payload.get("id")),
                stringValue(payload.get("userId")),
                stringValue(payload.get("transactionType")),
                doubleValue(payload.get("amount")),
                doubleValue(payload.get("balanceBefore")),
                doubleValue(payload.get("balanceAfter")),
                stringValue(payload.get("referenceId")),
                stringValue(payload.get("note")),
                parseDateTime(stringValue(payload.get("createdAt")))
        );
    }

    private Map<String, Double> lockedDeposits(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Double> deposits = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            deposits.put(String.valueOf(entry.getKey()), doubleValue(entry.getValue()));
        }
        return deposits;
    }

    private Item buildItem(Map<String, Object> payload) {
        String type = normalizeType(stringValue(payload.get("itemType")));
        Item item = ItemFactory.createItem(
                type,
                stringValue(payload.get("itemId")),
                stringValue(payload.get("itemName")),
                stringValue(payload.get("description")),
                doubleValue(payload.get("startingPrice")),
                parseDateTime(stringValue(payload.get("startTime"))),
                parseDateTime(stringValue(payload.get("endTime"))),
                "",
                0
        );
        item.setCurrentPrice(doubleValue(payload.get("currentPrice")));
        item.setSellerId(stringValue(payload.get("sellerId")));
        String approvalStatus = stringValue(payload.get("approvalStatus"));
        if (!approvalStatus.isBlank()) {
            item.setApprovalStatus(ApprovalStatus.valueOf(approvalStatus));
        }
        return item;
    }

    private AuctionEligibilityEntry buildAuctionEligibilityEntry(Map<String, Object> payload, double availableBalance) {
        double currentPrice = doubleValue(payload.get("currentPrice"));
        double minimumBid = doubleValue(payload.get("minimumNextBid"));
        double requiredDeposit = AuctionRules.requiredDeposit(currentPrice);
        boolean depositConfirmed = booleanValue(payload.get("depositConfirmed"));
        double effectiveAvailableBalance = payload.containsKey("availableBalance")
                ? doubleValue(payload.get("availableBalance"))
                : availableBalance;
        String status = stringValue(payload.get("status"));
        boolean finished = "FINISHED".equalsIgnoreCase(status)
                || "PAID".equalsIgnoreCase(status)
                || "CANCELLED".equalsIgnoreCase(status);
        boolean eligible = payload.containsKey("eligible")
                ? booleanValue(payload.get("eligible"))
                : !finished && (depositConfirmed || effectiveAvailableBalance >= requiredDeposit);
        return new AuctionEligibilityEntry(
                stringValue(payload.get("itemId")),
                stringValue(payload.get("itemName")),
                status,
                currentPrice,
                minimumBid,
                requiredDeposit,
                effectiveAvailableBalance,
                eligible,
                depositConfirmed,
                stringValue(payload.get("displayEndTime")),
                longValue(payload.get("secondsRemaining"))
        );
    }

    private AuctionListEntry buildAuctionListEntry(Map<String, Object> payload) {
        return new AuctionListEntry(
                stringValue(payload.get("itemId")),
                stringValue(payload.get("itemName")),
                stringValue(payload.get("status")),
                doubleValue(payload.get("currentPrice")),
                doubleValue(payload.get("minimumNextBid")),
                stringValue(payload.get("displayEndTime")),
                longValue(payload.get("secondsRemaining"))
        );
    }

    private AuctionDetail buildAuctionDetail(Map<String, Object> payload) {
        return new AuctionDetail(
                stringValue(payload.get("itemId")),
                stringValue(payload.get("itemName")),
                stringValue(payload.get("description")),
                stringValue(payload.get("status")),
                doubleValue(payload.get("currentPrice")),
                doubleValue(payload.get("minimumNextBid")),
                doubleValue(payload.get("requiredDeposit")),
                booleanValue(payload.get("depositConfirmed")),
                longValue(payload.get("secondsRemaining")),
                stringValue(payload.get("displayEndTime"))
        );
    }

    private Bid buildBid(Map<String, Object> payload) {
        return new Bid(
                stringValue(payload.get("id")),
                stringValue(payload.get("bidderId")),
                stringValue(payload.get("itemId")),
                doubleValue(payload.get("amount")),
                parseDateTime(stringValue(payload.get("bidTime")))
        );
    }

    private BidValidationResult buildBidResult(Map<String, Object> payload) {
        String status = stringValue(payload.get("status"));
        return new BidValidationResult(
                booleanValue(payload.get("accepted")),
                stringValue(payload.get("message")),
                doubleValue(payload.get("attemptedAmount")),
                doubleValue(payload.get("currentPrice")),
                doubleValue(payload.get("minimumAllowedBid")),
                status.isBlank() ? null : AuctionStatus.valueOf(status),
                parseDateTime(stringValue(payload.get("effectiveEndTime")))
        );
    }

    private AuctionDepositResult buildDepositResult(Map<String, Object> payload) {
        String status = stringValue(payload.get("status"));
        return new AuctionDepositResult(
                booleanValue(payload.get("accepted")),
                stringValue(payload.get("message")),
                doubleValue(payload.get("requiredDeposit")),
                doubleValue(payload.get("lockedDeposit")),
                status.isBlank() ? null : AuctionStatus.valueOf(status)
        );
    }

    private SettlementDetail buildSettlementFromResponse(Map<String, Object> response) {
        Object settlement = response.get("settlement");
        if (!(settlement instanceof Map<?, ?> map)) {
            throw new ApiClientException("API response did not include settlement details.");
        }
        return buildSettlementDetail(castMap(map));
    }

    private SettlementDetail buildSettlementDetail(Map<String, Object> payload) {
        return new SettlementDetail(
                stringValue(payload.get("itemId")),
                stringValue(payload.get("itemName")),
                stringValue(payload.get("sellerId")),
                stringValue(payload.get("status")),
                stringValue(payload.get("winnerBidderId")),
                doubleValue(payload.get("winningBidAmount")),
                doubleValue(payload.get("depositAmount")),
                doubleValue(payload.get("buyerPremiumAmount")),
                doubleValue(payload.get("totalBuyerDue")),
                doubleValue(payload.get("remainingPaymentDue")),
                doubleValue(payload.get("adminCommission")),
                doubleValue(payload.get("sellerPayout")),
                doubleValue(payload.get("lockedRemainingPayment")),
                doubleValue(payload.get("sellerReleasedAmount")),
                doubleValue(payload.get("buyerRefundedAmount")),
                stringValue(payload.get("buyerConfirmationDeadline")),
                stringValue(payload.get("displaySummary"))
        );
    }

    private NotificationDetail buildNotificationDetail(Map<String, Object> payload) {
        String popupKey = stringValue(payload.get("popupKey"));
        String title = stringValue(payload.get("title"));
        String body = stringValue(payload.get("body"));
        String displayText = stringValue(payload.get("displayText"));
        if (popupKey.isBlank()) {
            popupKey = title + "|" + body + "|" + displayText;
        }
        if (displayText.isBlank()) {
            displayText = title + (body.isBlank() ? "" : ": " + body);
        }
        return new NotificationDetail(popupKey, title, body, displayText);
    }

    private Map<String, Object> jsonObject(Object... fields) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int index = 0; index < fields.length; index += 2) {
            payload.put(String.valueOf(fields[index]), fields[index + 1]);
        }
        return payload;
    }

    private List<Map<String, Object>> objectList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(this::castMap)
                .toList();
    }

    private String normalizeType(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase();
        return switch (normalized) {
            case "electronics" -> "electronics";
            case "art" -> "art";
            case "vehicle" -> "vehicle";
            default -> {
                if (normalized.contains("art")) {
                    yield "art";
                }
                if (normalized.contains("vehicle")) {
                    yield "vehicle";
                }
                yield "electronics";
            }
        };
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        return LocalDateTime.parse(value, ISO_DATE_TIME);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : ISO_DATE_TIME.format(value);
    }

    private String segment(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Double.parseDouble(text);
        }
        return 0.0;
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return 0L;
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value instanceof String text && Boolean.parseBoolean(text);
    }

    private static String resolveBaseUrl() {
        String configured = System.getProperty("auction.api.baseUrl");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("AUCTION_API_BASE_URL");
        }
        if (configured == null || configured.isBlank()) {
            return "";
        }
        String trimmed = configured.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static Duration resolveTimeout(String propertyName, String environmentName, long defaultMillis) {
        String configured = System.getProperty(propertyName);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(environmentName);
        }
        if (configured == null || configured.isBlank()) {
            return Duration.ofMillis(defaultMillis);
        }
        try {
            long millis = Long.parseLong(configured.trim());
            return Duration.ofMillis(millis > 0L ? millis : defaultMillis);
        } catch (NumberFormatException ignored) {
            return Duration.ofMillis(defaultMillis);
        }
    }

    public record AuthResult(String token, User user) {
    }

    public record EntryDepositResponse(AuctionDepositResult result, User user) {
    }

    public record AuctionDetail(
            String itemId,
            String itemName,
            String description,
            String status,
            double currentPrice,
            double minimumNextBid,
            double requiredDeposit,
            boolean depositConfirmed,
            long secondsRemaining,
            String displayEndTime
    ) {
    }

    public record SettlementDetail(
            String itemId,
            String itemName,
            String sellerId,
            String status,
            String winnerBidderId,
            double winningBidAmount,
            double depositAmount,
            double buyerPremiumAmount,
            double totalBuyerDue,
            double remainingPaymentDue,
            double adminCommission,
            double sellerPayout,
            double lockedRemainingPayment,
            double sellerReleasedAmount,
            double buyerRefundedAmount,
            String buyerConfirmationDeadline,
            String displaySummary
    ) {
    }

    public record CurrentUserSnapshot(User user, WalletSummary wallet) {
    }

    public record NotificationDetail(String popupKey, String title, String body, String displayText) {
    }

    public record ConnectionTestResult(String baseUrl, String status, String serverTime) {
    }

    public static class ApiClientException extends RuntimeException {
        private final int statusCode;

        public ApiClientException(String message) {
            this(message, null, -1);
        }

        public ApiClientException(String message, Throwable cause) {
            this(message, cause, -1);
        }

        public ApiClientException(String message, int statusCode) {
            this(message, null, statusCode);
        }

        public ApiClientException(String message, Throwable cause, int statusCode) {
            super(message, cause);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
