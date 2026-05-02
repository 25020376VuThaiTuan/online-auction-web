package org.example.server;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.example.auction.AuctionStatus;
import org.example.auction.AuctionSummary;
import org.example.auction.BidValidationResult;
import org.example.exception.InvalidPasswordException;
import org.example.exception.UserNotFound;
import org.example.model.Bid;
import org.example.model.Item;
import org.example.model.User;
import org.example.service.AuthenticationService;
import org.example.service.AuctionWorkflowService;
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
                            "/api/auth/me",
                            "/api/auth/logout",
                            "/api/auctions",
                            "/api/auctions/{id}",
                            "/api/auctions/{id}/bids",
                            "/api/events/stream?token={token}"
                    )
            ));
            return;
        }

        String rootSegment = segments.get(0);
        switch (rootSegment) {
            case "health" -> handleHealth(exchange);
            case "auth" -> handleAuth(exchange, segments);
            case "auctions" -> handleAuctions(exchange, segments);
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
                        "user", userPayload(user)
                ));
                return;
            } catch (UserNotFound | InvalidPasswordException e) {
                throw new ApiHttpException(401, e.getMessage());
            }
        }

        if (segments.size() == 2 && "me".equals(segments.get(1))) {
            requireMethod(exchange, "GET");
            User user = requireAuthenticatedUser(exchange, false);
            sendJson(exchange, 200, jsonObject(
                    "user", userPayload(user)
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

    private void handleAuctions(HttpExchange exchange, List<String> segments) throws IOException {
        requireAuthenticatedUser(exchange, false);

        if (segments.size() == 1) {
            requireMethod(exchange, "GET");
            List<Map<String, Object>> auctions = new ArrayList<>();
            for (AuctionListEntry entry : workflowService.getAuctionListEntries()) {
                auctions.add(auctionPayload(entry.getItemId()));
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
                    "auction", auctionPayload(itemId)
            ));
            return;
        }

        if (segments.size() == 3 && "bids".equals(segments.get(2))) {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 200, jsonObject(
                        "itemId", itemId,
                        "bids", bidHistoryPayload(itemId)
                ));
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                User user = requireAuthenticatedUser(exchange, false);
                Map<String, Object> request = ApiJson.parseObject(readRequestBody(exchange));
                double amount = ApiJson.requireDouble(request, "amount");
                BidValidationResult result = workflowService.placeBid(itemId, user, amount);

                Map<String, Object> response = jsonObject(
                        "accepted", result.accepted(),
                        "message", result.message(),
                        "attemptedAmount", result.attemptedAmount(),
                        "currentPrice", result.currentPrice(),
                        "minimumAllowedBid", result.minimumAllowedBid(),
                        "status", result.status().name(),
                        "effectiveEndTime", formatDateTime(result.effectiveEndTime()),
                        "auction", auctionPayload(itemId)
                );

                if (result.accepted()) {
                    realtimeBroker.publish("auction-updated", jsonObject(
                            "type", "BID_ACCEPTED",
                            "auction", auctionPayload(itemId),
                            "latestBid", latestBidPayload(itemId),
                            "serverTime", formatDateTime(LocalDateTime.now())
                    ));
                    sendJson(exchange, 201, response);
                    return;
                }

                sendJson(exchange, 409, response);
                return;
            }
        }

        throw new ApiHttpException(404, "Unknown auction route.");
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
                    "user", userPayload(user),
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

    private List<Map<String, Object>> bidHistoryPayload(String itemId) {
        List<Map<String, Object>> bids = new ArrayList<>();
        for (Bid bid : workflowService.getBidHistory(itemId)) {
            bids.add(bidPayload(bid));
        }
        return bids;
    }

    private Map<String, Object> latestBidPayload(String itemId) {
        List<Bid> bids = workflowService.getBidHistory(itemId);
        if (bids.isEmpty()) {
            return null;
        }
        return bidPayload(bids.get(bids.size() - 1));
    }

    private Map<String, Object> bidPayload(Bid bid) {
        return jsonObject(
                "id", bid.getId(),
                "itemId", bid.getItemId(),
                "bidderId", bid.getBidderId(),
                "amount", bid.getAmount(),
                "bidTime", formatDateTime(bid.getBidTime())
        );
    }

    private Map<String, Object> auctionPayload(String itemId) {
        Item item = workflowService.findItemById(itemId)
                .orElseThrow(() -> new ApiHttpException(404, "Auction not found: " + itemId));
        AuctionSummary summary = workflowService.getSummary(itemId);

        return jsonObject(
                "itemId", item.getId(),
                "itemName", item.getItemName(),
                "description", item.getDescription(),
                "itemType", item.getClass().getSimpleName(),
                "status", summary.status().name(),
                "startingPrice", item.getStartingPrice(),
                "currentPrice", summary.currentPrice(),
                "minimumNextBid", summary.minimumNextBid(),
                "secondsRemaining", summary.secondsRemaining(),
                "acceptingBids", summary.status() == AuctionStatus.RUNNING,
                "totalBids", summary.totalBids(),
                "highestBidderId", summary.highestBidderId(),
                "startTime", formatDateTime(item.getStartTime()),
                "endTime", formatDateTime(item.getEndTime()),
                "displayEndTime", item.getEndTimeString()
        );
    }

    private Map<String, Object> userPayload(User user) {
        return jsonObject(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail(),
                "role", user.getRole()
        );
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
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : ISO_DATE_TIME.format(value);
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
