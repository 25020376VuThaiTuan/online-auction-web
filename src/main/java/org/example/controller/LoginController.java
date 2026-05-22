package org.example.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.example.client.AuctionApiClient;
import org.example.exception.InvalidPasswordException;
import org.example.exception.UserNotFound;
import org.example.service.AuthenticationService;
import org.example.state.ApplicationSession;
import org.example.util.BackgroundExecutorFactory;
import org.example.util.SceneNavigator;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

public class LoginController {
    private final AuctionApiClient apiClient;
    private final AuthenticationService authenticationService;
    private final ApplicationSession applicationSession;
    private final ExecutorService connectionTestExecutor =
            BackgroundExecutorFactory.newSingleThreadExecutor("api-connection-test");

    public LoginController() {
        this(
                AuctionApiClient.getInstance(),
                AuthenticationService.getInstance(),
                ApplicationSession.getInstance()
        );
    }

    LoginController(
            AuctionApiClient apiClient,
            AuthenticationService authenticationService,
            ApplicationSession applicationSession
    ) {
        this.apiClient = apiClient;
        this.authenticationService = authenticationService;
        this.applicationSession = applicationSession;
    }

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button testConnectionButton;

    @FXML
    private Label hintLabel;

    @FXML
    public void initialize() {
        hintLabel.setText(apiClient.isEnabled()
                ? ""
                : authenticationService.getLoginHint());
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Missing credentials", "Enter both username and password.");
            return;
        }

        try {
            authenticate(username, password);
            SceneNavigator.switchScene(usernameField, "/view/Dashboard.fxml", "Auction Dashboard");
        } catch (UserNotFound e) {
            showAlert(Alert.AlertType.WARNING, "User not found", e.getMessage());
        } catch (InvalidPasswordException e) {
            showAlert(Alert.AlertType.WARNING, "Password incorrect", e.getMessage());
        } catch (AuctionApiClient.ApiClientException e) {
            showAlert(
                    Alert.AlertType.WARNING,
                    AuctionApiClient.isConnectivityFailure(e) ? "API unavailable" : "Server login failed",
                    e.getMessage()
            );
        } catch (RuntimeException e) {
            applicationSession.logout();
            showAlert(Alert.AlertType.WARNING, "Dashboard unavailable", failureMessage(e));
        }
    }

    @FXML
    private void handleTestConnection() {
        if (!apiClient.isEnabled()) {
            showAlert(
                    Alert.AlertType.INFORMATION,
                    "API not configured",
                    "AUCTION_API_BASE_URL is not set. The app will use local demo data until an API base URL is configured."
            );
            return;
        }

        String previousText = testConnectionButton.getText();
        testConnectionButton.setText("Testing...");
        testConnectionButton.setDisable(true);

        CompletableFuture
                .supplyAsync(apiClient::testConnection, connectionTestExecutor)
                .whenComplete((result, throwable) -> Platform.runLater(() -> {
                    testConnectionButton.setText(previousText);
                    testConnectionButton.setDisable(false);

                    if (throwable == null) {
                        showConnectionSuccess(result);
                    } else {
                        showConnectionFailure(unwrapCompletionException(throwable));
                    }
                }));
    }

    @FXML
    private void handleOpenRegistration() {
        SceneNavigator.switchScene(usernameField, "/view/Register.fxml", "Create Account");
    }

    void authenticate(String username, String password) throws UserNotFound, InvalidPasswordException {
        if (!apiClient.isEnabled()) {
            applicationSession.login(authenticationService.loginOrThrow(username, password));
            return;
        }

        try {
            AuctionApiClient.AuthResult result = apiClient.login(username, password);
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
                        + "API server is running, then try signing in again. Local demo sign-in is used only when "
                        + "AUCTION_API_BASE_URL is not set.",
                cause
        );
    }

    private void showConnectionSuccess(AuctionApiClient.ConnectionTestResult result) {
        String serverTime = result.serverTime().isBlank() ? "" : "\nServer time: " + result.serverTime();
        showAlert(
                Alert.AlertType.INFORMATION,
                "API connection successful",
                "Connected to " + result.baseUrl() + "." + serverTime
        );
    }

    private void showConnectionFailure(Throwable failure) {
        if (failure instanceof AuctionApiClient.ApiClientException apiFailure) {
            showAlert(
                    Alert.AlertType.WARNING,
                    AuctionApiClient.isConnectivityFailure(apiFailure) ? "API unavailable" : "API health check failed",
                    apiFailure.getMessage()
            );
            return;
        }

        showAlert(Alert.AlertType.WARNING, "API health check failed", failureMessage(failure));
    }

    private Throwable unwrapCompletionException(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private String failureMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                return message;
            }
            current = current.getCause();
        }
        return "Dashboard data could not be loaded.";
    }
}
