package org.example.server;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AuctionApiBootstrap {
    private static final Logger LOGGER = Logger.getLogger(AuctionApiBootstrap.class.getName());
    private static final String BASE_URL_PROPERTY = "auction.api.baseUrl";
    private static final String BASE_URL_ENV = "AUCTION_API_BASE_URL";
    private static final Duration HEALTH_TIMEOUT = Duration.ofMillis(750);
    private static final AtomicBoolean BOOTSTRAP_ATTEMPTED = new AtomicBoolean(false);

    private AuctionApiBootstrap() {
    }

    public static void startLocalServerIfConfigured() {
        if (!BOOTSTRAP_ATTEMPTED.compareAndSet(false, true)) {
            return;
        }

        String baseUrl = resolveBaseUrl();
        if (!shouldBootstrap(baseUrl) || isHealthy(baseUrl)) {
            return;
        }

        URI baseUri = URI.create(baseUrl);
        try {
            AuctionApiServerMain.main(new String[]{"--port=" + baseUri.getPort()});
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Embedded API server startup failed.", e);
        }
    }

    static String resolveBaseUrl() {
        String configured = System.getProperty(BASE_URL_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(BASE_URL_ENV);
        }
        if (configured == null || configured.isBlank()) {
            return "";
        }
        String trimmed = configured.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    static boolean shouldBootstrap(String baseUrl) {
        URI baseUri = parseBaseUri(baseUrl);
        return baseUri != null
                && "http".equalsIgnoreCase(baseUri.getScheme())
                && baseUri.getPort() > 0
                && isLocalHost(baseUri.getHost());
    }

    static URI parseBaseUri(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        try {
            return URI.create(baseUrl.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static boolean isLocalHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized)
                || "0.0.0.0".equals(normalized);
    }

    private static boolean isHealthy(String baseUrl) {
        URI healthUri = parseBaseUri(baseUrl + "/health");
        if (healthUri == null) {
            return false;
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(HEALTH_TIMEOUT)
                .build();
        HttpRequest request = HttpRequest.newBuilder(healthUri)
                .timeout(HEALTH_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
