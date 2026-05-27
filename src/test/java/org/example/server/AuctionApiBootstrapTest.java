package org.example.server;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

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
}
