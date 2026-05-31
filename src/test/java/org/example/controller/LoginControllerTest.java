package org.example.controller;

import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.example.client.AuctionApiClient;
import org.example.exception.InvalidPasswordException;
import org.example.model.Bidder;
import org.example.service.AuthenticationService;
import org.example.state.ApplicationSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LoginControllerTest {

    private LoginController controller;

    @BeforeAll
    static void initToolkit() {
        JavaFxTestSupport.startToolkit();
    }

    @BeforeEach
    void setup() throws Exception {

        controller = new LoginController(
                newApiClientWithoutBaseUrl(),
                AuthenticationService.getInstance(),
                ApplicationSession.getInstance()
        );

        setField(
                "usernameField",
                new TextField()
        );

        setField(
                "passwordField",
                new PasswordField()
        );

        setField(
                "hintLabel",
                new Label()
        );
    }

    @AfterEach
    void clearSession() {
        ApplicationSession.getInstance().logout();
    }

    private void setField(
            String fieldName,
            Object value
    ) throws Exception {

        Field field =
                LoginController.class
                        .getDeclaredField(fieldName);

        field.setAccessible(true);

        field.set(controller, value);
    }

    private Object getField(
            String fieldName
    ) throws Exception {

        Field field =
                LoginController.class
                        .getDeclaredField(fieldName);

        field.setAccessible(true);

        return field.get(controller);
    }

    private AuctionApiClient newApiClientWithoutBaseUrl()
            throws Exception {

        String previousBaseUrl =
                System.getProperty(
                        "auction.api.baseUrl"
                );

        try {
            System.clearProperty(
                    "auction.api.baseUrl"
            );

            Constructor<AuctionApiClient> constructor =
                    AuctionApiClient.class
                            .getDeclaredConstructor();

            constructor.setAccessible(
                    true
            );

            return constructor.newInstance();
        } finally {
            if (previousBaseUrl == null) {
                System.clearProperty(
                        "auction.api.baseUrl"
                );
            } else {
                System.setProperty(
                        "auction.api.baseUrl",
                        previousBaseUrl
                );
            }
        }
    }

    @Test
    void shouldInitializeHintLabel()
            throws Exception {

        controller.initialize();

        Label label =
                (Label) getField(
                        "hintLabel"
                );

        assertNotNull(label.getText());
    }

    @Test
    void shouldSetUsername()
            throws Exception {

        TextField username =
                (TextField) getField(
                        "usernameField"
                );

        username.setText("admin");

        assertEquals(
                "admin",
                username.getText()
        );
    }

    @Test
    void shouldSetPassword()
            throws Exception {

        PasswordField password =
                (PasswordField) getField(
                        "passwordField"
                );

        password.setText("123456");

        assertEquals(
                "123456",
                password.getText()
        );
    }

    @Test
    void shouldReturnFailureMessage()
            throws Exception {

        Method method =
                LoginController.class
                        .getDeclaredMethod(
                                "failureMessage",
                                Throwable.class
                        );

        method.setAccessible(true);

        String result =
                (String) method.invoke(
                        controller,
                        new RuntimeException("error")
                );

        assertEquals(
                "error",
                result
        );
    }

    @Test
    void shouldReturnDefaultFailureMessage()
            throws Exception {

        Method method =
                LoginController.class
                        .getDeclaredMethod(
                                "failureMessage",
                                Throwable.class
                        );

        method.setAccessible(true);

        String result =
                (String) method.invoke(
                        controller,
                        new RuntimeException()
                );

        assertEquals(
                "Dashboard data could not be loaded.",
                result
        );
    }

    @Test
    void authenticateLogsInLocalUserAndRejectsBadPassword()
            throws Exception {

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "login_" + suffix;
        Bidder bidder = (Bidder) AuthenticationService.getInstance().registerManualBidder(
                username,
                "secret",
                username + "@test.local",
                "Login Controller " + suffix
        );

        controller.authenticate(username, "secret");

        assertEquals(
                bidder.getId(),
                ApplicationSession.getInstance().getCurrentUser().orElseThrow().getId()
        );

        ApplicationSession.getInstance().logout();

        assertThrows(
                InvalidPasswordException.class,
                () -> controller.authenticate(username, "wrong")
        );
    }

    @Test
    void resetPasswordUpdatesLocalLoginCredential()
            throws Exception {

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "login_reset_" + suffix;
        AtomicReference<String> deliveredCode = new AtomicReference<>();
        AuthenticationService service = new AuthenticationService(
                List.of(org.example.repository.DemoUserRepository.createEmpty()),
                (email, recoveryCode) -> deliveredCode.set(recoveryCode)
        );
        controller = new LoginController(
                newApiClientWithoutBaseUrl(),
                service,
                ApplicationSession.getInstance()
        );
        service.registerManualBidder(
                username,
                "oldpass",
                username + "@test.local",
                "Login Reset " + suffix
        );

        controller.requestPasswordRecovery(username, username + "@test.local");
        controller.resetPassword(username, username + "@test.local", deliveredCode.get(), "newpass", "newpass");

        controller.authenticate(username, "newpass");
        assertEquals(
                username,
                ApplicationSession.getInstance().getCurrentUser().orElseThrow().getUsername()
        );
        ApplicationSession.getInstance().logout();
        assertThrows(
                InvalidPasswordException.class,
                () -> controller.authenticate(username, "oldpass")
        );
    }

    @Test
    void configuredApiUnavailableMessageExplainsLocalFallbackBoundary()
            throws Exception {

        Method method =
                LoginController.class
                        .getDeclaredMethod(
                                "configuredApiUnavailable",
                                AuctionApiClient.ApiClientException.class
                        );

        method.setAccessible(true);

        AuctionApiClient.ApiClientException result =
                (AuctionApiClient.ApiClientException) method.invoke(
                        controller,
                        new AuctionApiClient.ApiClientException("offline")
                );

        assertTrue(
                result.getMessage().contains("Local demo sign-in is used only when AUCTION_API_BASE_URL is not set.")
        );
    }
}
