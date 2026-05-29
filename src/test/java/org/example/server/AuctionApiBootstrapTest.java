package org.example.server;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionApiBootstrapTest {
    @Test
    void localHttpBaseUrlWithExplicitPortBootstrapsServer() {
        assertTrue(AuctionApiBootstrap.shouldBootstrap("http://localhost:8081/api"));
        assertTrue(AuctionApiBootstrap.shouldBootstrap("http://127.0.0.1:8081/api"));
        assertTrue(AuctionApiBootstrap.shouldBootstrap("http://[::1]:8081/api"));
    }

    @Test
    void remoteBaseUrlDoesNotBootstrapServer() {
        assertFalse(AuctionApiBootstrap.shouldBootstrap("http://api.example.com:8081/api"));
    }

    @Test
    void missingPortDoesNotBootstrapServer() {
        assertFalse(AuctionApiBootstrap.shouldBootstrap("http://localhost/api"));
    }

    @Test
    void invalidBaseUrlDoesNotBootstrapServer() {
        assertFalse(AuctionApiBootstrap.shouldBootstrap("not a url"));
    }

    @Test
    void resolvesConfiguredBaseUrlAndNormalizesTrailingSlash() {
        String previousBaseUrl = System.getProperty("auction.api.baseUrl");
        try {
            System.setProperty("auction.api.baseUrl", " http://localhost:8081/api/ ");

            assertEquals("http://localhost:8081/api", AuctionApiBootstrap.resolveBaseUrl());
        } finally {
            if (previousBaseUrl == null) {
                System.clearProperty("auction.api.baseUrl");
            } else {
                System.setProperty("auction.api.baseUrl", previousBaseUrl);
            }
        }
    }

    @Test
    void parseAndLocalHostHelpersHandleBlankBracketedAndWildcardHosts() {
        assertNull(AuctionApiBootstrap.parseBaseUri(null));
        assertNull(AuctionApiBootstrap.parseBaseUri(" "));
        assertNotNull(AuctionApiBootstrap.parseBaseUri("http://localhost:8081/api"));

        assertFalse(AuctionApiBootstrap.isLocalHost(null));
        assertFalse(AuctionApiBootstrap.isLocalHost(" "));
        assertTrue(AuctionApiBootstrap.isLocalHost(" LOCALHOST "));
        assertTrue(AuctionApiBootstrap.isLocalHost("[::1]"));
        assertTrue(AuctionApiBootstrap.isLocalHost("0.0.0.0"));
        assertFalse(AuctionApiBootstrap.isLocalHost("example.test"));
    }

    @Test
    void startLocalServerIsOneShotAndNoopsForRemoteBaseUrl() throws Exception {
        resetBootstrapAttempt();
        String previousBaseUrl = System.getProperty("auction.api.baseUrl");
        try {
            System.setProperty("auction.api.baseUrl", "http://api.example.test:8081/api");

            AuctionApiBootstrap.startLocalServerIfConfigured();
            AuctionApiBootstrap.startLocalServerIfConfigured();

            assertTrue(bootstrapAttempted().get());
        } finally {
            if (previousBaseUrl == null) {
                System.clearProperty("auction.api.baseUrl");
            } else {
                System.setProperty("auction.api.baseUrl", previousBaseUrl);
            }
            resetBootstrapAttempt();
        }
    }

    @Test
    void privateHealthCheckHandlesHealthyUnhealthyAndInvalidTargets() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/health", exchange -> {
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();

        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/api";

            assertTrue(isHealthy(baseUrl));
            assertFalse(isHealthy("http://127.0.0.1:" + server.getAddress().getPort() + "/missing"));
            assertFalse(isHealthy("not a url"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void privateConstructorCanBeInvokedForCoverage() throws Exception {
        Constructor<AuctionApiBootstrap> constructor = AuctionApiBootstrap.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertNotNull(constructor.newInstance());
    }

    private static void resetBootstrapAttempt() throws Exception {
        bootstrapAttempted().set(false);
    }

    private static AtomicBoolean bootstrapAttempted() throws Exception {
        Field field = AuctionApiBootstrap.class.getDeclaredField("BOOTSTRAP_ATTEMPTED");
        field.setAccessible(true);
        return (AtomicBoolean) field.get(null);
    }

    private static boolean isHealthy(String baseUrl) throws Exception {
        Method method = AuctionApiBootstrap.class.getDeclaredMethod("isHealthy", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, baseUrl);
    }
}
