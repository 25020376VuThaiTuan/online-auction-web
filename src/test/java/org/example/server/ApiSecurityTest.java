package org.example.server;

import org.example.client.AuctionApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiSecurityTest {

    private AuctionApiClient client;

    @BeforeEach
    void setUp() {
        client = AuctionApiClient.getInstance();
    }

    @Test
    void shouldRejectAccessWithoutToken() {

        assertThrows(Exception.class, () -> {
            client.getCurrentUser(null);
        });
    }

    @Test
    void shouldRejectInvalidToken() {

        assertThrows(Exception.class, () -> {
            client.getCurrentUser("invalid-token");
        });
    }

    @Test
    void shouldAllowAuthenticatedUser() {

        var auth = client.login("admin", "admin123");

        assertNotNull(auth);
        assertNotNull(auth.token());

        var currentUser = client.getCurrentUser(auth.token());

        assertNotNull(currentUser);
    }

    @Test
    void shouldAllowAdminAccess() {

        var auth = client.login("admin", "admin123");

        assertDoesNotThrow(() -> {
            client.getAllUsers(auth.token());
        });
    }

    @Test
    void shouldRejectNormalUserAccessToAdminFeature() {

        var auth = client.registerManualBidder(
                "security_bidder",
                "password123",
                "security@test.com",
                "Security Bidder"
        );

        assertThrows(Exception.class, () -> {
            client.getAllUsers(auth.token());
        });
    }
}