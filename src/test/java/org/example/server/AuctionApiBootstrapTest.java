package org.example.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
