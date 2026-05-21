package org.example.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.example.client.AuctionApiClient;
import org.example.service.AuthenticationService;
import org.example.state.ApplicationSession;
import org.example.util.AccountInputValidator;
import org.example.util.SceneNavigator;

public class RegisterController {
    private final AuctionApiClient apiClient;
    private final AuthenticationService authenticationService;
    private final ApplicationSession applicationSession;

    public RegisterController() {
        this(
                AuctionApiClient.getInstance(),
                AuthenticationService.getInstance(),
                ApplicationSession.getInstance()
        );
    }

    RegisterController(
            AuctionApiClient apiClient,
            AuthenticationService authenticationService,
            ApplicationSession applicationSession
    ) {
        this.apiClient = apiClient;
        this.authenticationService = authenticationService;
        this.applicationSession = applicationSession;
    }

    @FXML
    private ChoiceBox<String> accountRoleChoiceBox;

    @FXML
    private TextField fullNameField;

    @FXML
    private TextField usernameField;

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    public void initialize() {
        accountRoleChoiceBox.setItems(FXCollections.observableArrayList("BIDDER", "SELLER"));
        accountRoleChoiceBox.setValue("BIDDER");
    }

    @FXML
    private void handleRegister() {
        String accountRole = value(accountRoleChoiceBox.getValue());
        String fullName = value(fullNameField.getText());
        String username = value(usernameField.getText());
        String email = value(emailField.getText());
        String password = passwordField.getText() == null ? "" : passwordField.getText();
        String confirmPassword = confirmPasswordField.getText() == null ? "" : confirmPasswordField.getText();

        if (!password.equals(confirmPassword)) {
            showAlert(Alert.AlertType.WARNING, "Password mismatch", "Confirm password must match the password.");
            return;
        }

        try {
            AccountInputValidator.RegistrationInput registration = AccountInputValidator.validateRegistration(
                    username,
                    password,
                    email,
                    fullName
            );
            register(
                    accountRole,
                    registration.username(),
                    registration.password(),
                    registration.email(),
                    registration.fullName()
            );
            SceneNavigator.switchScene(usernameField, "/view/Dashboard.fxml", "Auction Dashboard");
        } catch (AuctionApiClient.ApiClientException e) {
            showAlert(
                    Alert.AlertType.WARNING,
                    AuctionApiClient.isConnectivityFailure(e) ? "API unavailable" : "Registration failed",
                    e.getMessage()
            );
        } catch (IllegalArgumentException e) {
            showAlert(Alert.AlertType.WARNING, "Registration failed", e.getMessage());
        }
    }

    @FXML
    private void handleBackToLogin() {
        SceneNavigator.switchScene(usernameField, "/view/Login.fxml", "Online Auction System");
    }

    private String value(String text) {
        return text == null ? "" : text.trim();
    }

    void register(String accountRole, String username, String password, String email, String fullName) {
        if (!apiClient.isEnabled()) {
            applicationSession.login(registerLocally(accountRole, username, password, email, fullName));
            return;
        }

        try {
            AuctionApiClient.AuthResult result = "SELLER".equalsIgnoreCase(accountRole)
                    ? apiClient.registerManualSeller(username, password, email, fullName)
                    : apiClient.registerManualBidder(username, password, email, fullName);
            applicationSession.login(result.user(), result.token());
        } catch (AuctionApiClient.ApiClientException e) {
            if (!AuctionApiClient.isConnectivityFailure(e)) {
                throw e;
            }
            throw configuredApiUnavailable(e);
        }
    }

    private AuctionApiClient.ApiClientException configuredApiUnavailable(AuctionApiClient.ApiClientException cause) {
        return new AuctionApiClient.ApiClientException(
                "Could not reach the configured auction API server. Check AUCTION_API_BASE_URL and make sure the "
                        + "API server is running, then try creating the account again. Local demo registration is "
                        + "used only when AUCTION_API_BASE_URL is not set.",
                cause
        );
    }

    private org.example.model.User registerLocally(
            String accountRole,
            String username,
            String password,
            String email,
            String fullName
    ) {
        return "SELLER".equalsIgnoreCase(accountRole)
                ? authenticationService.registerManualSeller(username, password, email, fullName)
                : authenticationService.registerManualBidder(username, password, email, fullName);
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
