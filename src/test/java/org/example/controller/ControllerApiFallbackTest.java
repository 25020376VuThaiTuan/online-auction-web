package org.example.controller;

import org.example.client.AuctionApiClient;
import org.example.repository.DemoUserRepository;
import org.example.repository.UserRepository;
import org.example.service.AuthenticationService;
import org.example.state.ApplicationSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.net.ServerSocket;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ControllerApiFallbackTest {
    private final ApplicationSession applicationSession = ApplicationSession.getInstance();

    @BeforeEach
    void resetSession() {
        applicationSession.logout();
    }

    @AfterEach
    void clearSession() {
        applicationSession.logout();
    }

    @Test
    void configuredApiLoginConnectivityFailureDoesNotUseLocalDemoAccount() throws Exception {
        AuthenticationService localAuthentication = authenticationService(DemoUserRepository.getInstance());
        LoginController controller = new LoginController(
                unreachableApiClient(),
                localAuthentication,
                applicationSession
        );

        AuctionApiClient.ApiClientException exception = assertThrows(
                AuctionApiClient.ApiClientException.class,
                () -> controller.authenticate("bidder", "bid123")
        );

        assertTrue(exception.getMessage().contains("AUCTION_API_BASE_URL"));
        assertTrue(exception.getMessage().contains("API server is running"));
        assertTrue(applicationSession.getCurrentUser().isEmpty());
    }

    @Test
    void configuredApiRegistrationConnectivityFailureDoesNotCreateLocalAccount() throws Exception {
        NoLocalWritesUserRepository repository = new NoLocalWritesUserRepository();
        AuthenticationService localAuthentication = authenticationService(repository);
        RegisterController controller = new RegisterController(
                unreachableApiClient(),
                localAuthentication,
                applicationSession
        );

        AuctionApiClient.ApiClientException exception = assertThrows(
                AuctionApiClient.ApiClientException.class,
                () -> controller.register(
                        "BIDDER",
                        "remote_user",
                        "secure123",
                        "remote_user@test.local",
                        "Remote User"
                )
        );

        assertTrue(exception.getMessage().contains("AUCTION_API_BASE_URL"));
        assertTrue(exception.getMessage().contains("API server is running"));
        assertTrue(localAuthentication.findByUsername("remote_user").isEmpty());
        assertTrue(applicationSession.getCurrentUser().isEmpty());
    }

    private AuctionApiClient unreachableApiClient() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        return newApiClient("http://127.0.0.1:" + port + "/api");
    }

    private AuctionApiClient newApiClient(String baseUrl) throws Exception {
        String previousBaseUrl = System.getProperty("auction.api.baseUrl");
        try {
            System.setProperty("auction.api.baseUrl", baseUrl);
            Constructor<AuctionApiClient> constructor = AuctionApiClient.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } finally {
            if (previousBaseUrl == null) {
                System.clearProperty("auction.api.baseUrl");
            } else {
                System.setProperty("auction.api.baseUrl", previousBaseUrl);
            }
        }
    }

    private AuthenticationService authenticationService(UserRepository repository) throws Exception {
        Constructor<AuthenticationService> constructor = AuthenticationService.class.getDeclaredConstructor(List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(List.of(repository));
    }

    private static final class NoLocalWritesUserRepository implements UserRepository {
        @Override
        public Optional<org.example.model.User> findByUsername(String username) {
            return Optional.empty();
        }

        @Override
        public Optional<org.example.model.User> findByEmail(String email) {
            return Optional.empty();
        }

        @Override
        public Optional<org.example.model.User> save(org.example.model.User user) {
            throw new AssertionError("Configured API registration must not create local accounts.");
        }
    }
}
