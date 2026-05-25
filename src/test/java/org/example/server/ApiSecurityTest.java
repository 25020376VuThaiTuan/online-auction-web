package org.example.server;

import com.sun.net.httpserver.HttpServer;
import org.example.client.AuctionApiClient;
import org.example.service.AuthenticationService;
import org.example.service.AuctionWorkflowService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class ApiSecurityTest {

    private HttpServer server;
    private String previousBaseUrl;
    private AuctionApiClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", new AuctionApiHandler(
                AuthenticationService.getInstance(),
                AuctionWorkflowService.getInstance(),
                new ApiSessionService(),
                new AuctionRealtimeBroker()
        ));
        server.start();

        previousBaseUrl = System.getProperty("auction.api.baseUrl");
        System.setProperty("auction.api.baseUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/api");
        client = newApiClient();
    }

    @AfterEach
    void tearDown() {
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

    private AuctionApiClient newApiClient() throws Exception {
        Constructor<AuctionApiClient> constructor = AuctionApiClient.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
