package org.example.controller;

import javafx.scene.control.ChoiceBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.example.client.AuctionApiClient;
import org.example.state.ApplicationSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RegisterControllerTest {

    private RegisterController controller;

    @BeforeAll
    static void initToolkit() {
        JavaFxTestSupport.startToolkit();
    }

    @BeforeEach
    void setup() throws Exception {

        controller = new RegisterController(
                newApiClientWithoutBaseUrl(),
                org.example.service.AuthenticationService.getInstance(),
                ApplicationSession.getInstance()
        );

        setField(
                "accountRoleChoiceBox",
                new ChoiceBox<String>()
        );

        setField(
                "fullNameField",
                new TextField()
        );

        setField(
                "usernameField",
                new TextField()
        );

        setField(
                "emailField",
                new TextField()
        );

        setField(
                "passwordField",
                new PasswordField()
        );

        setField(
                "confirmPasswordField",
                new PasswordField()
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
                RegisterController.class
                        .getDeclaredField(fieldName);

        field.setAccessible(true);

        field.set(controller, value);
    }

    private Object getField(
            String fieldName
    ) throws Exception {

        Field field =
                RegisterController.class
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
    void shouldInitializeChoiceBox()
            throws Exception {

        controller.initialize();

        @SuppressWarnings("unchecked")
        ChoiceBox<String> box =
                (ChoiceBox<String>) getField(
                        "accountRoleChoiceBox"
                );

        assertEquals(
                "BIDDER",
                box.getValue()
        );

        assertEquals(
                2,
                box.getItems().size()
        );
    }

    @Test
    void shouldTrimValue()
            throws Exception {

        Method method =
                RegisterController.class
                        .getDeclaredMethod(
                                "value",
                                String.class
                        );

        method.setAccessible(true);

        String result =
                (String) method.invoke(
                        controller,
                        "  hello  "
                );

        assertEquals(
                "hello",
                result
        );
    }

    @Test
    void shouldReturnEmptyForNull()
            throws Exception {

        Method method =
                RegisterController.class
                        .getDeclaredMethod(
                                "value",
                                String.class
                        );

        method.setAccessible(true);

        String result =
                (String) method.invoke(
                        controller,
                        new Object[]{null}
                );

        assertEquals(
                "",
                result
        );
    }

    @Test
    void shouldSetFieldsCorrectly()
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
    void shouldSetPasswordField()
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
    void registerCreatesLocalBidderAndSellerAndLogsThemIn()
            throws Exception {

        String bidderSuffix = UUID.randomUUID().toString().substring(0, 8);
        String bidderUsername = "regbid_" + bidderSuffix;
        controller.register(
                "BIDDER",
                bidderUsername,
                "secret",
                bidderUsername + "@test.local",
                "Register Bidder " + bidderSuffix
        );

        assertEquals(
                "BIDDER",
                ApplicationSession.getInstance().getCurrentUser().orElseThrow().getRole()
        );

        ApplicationSession.getInstance().logout();

        String sellerSuffix = UUID.randomUUID().toString().substring(0, 8);
        String sellerUsername = "regsel_" + sellerSuffix;
        controller.register(
                "SELLER",
                sellerUsername,
                "secret",
                sellerUsername + "@test.local",
                "Register Seller " + sellerSuffix
        );

        assertEquals(
                "SELLER",
                ApplicationSession.getInstance().getCurrentUser().orElseThrow().getRole()
        );
    }

    @Test
    void configuredApiUnavailableMessageExplainsRegistrationFallbackBoundary()
            throws Exception {

        Method method =
                RegisterController.class
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
                result.getMessage().contains("Local demo registration is used only when AUCTION_API_BASE_URL is not set.")
        );
    }
}
