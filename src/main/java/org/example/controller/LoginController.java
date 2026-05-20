package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.example.client.AuctionApiClient;
import org.example.exception.InvalidPasswordException;
import org.example.exception.UserNotFound;
import org.example.service.AuthenticationService;
import org.example.state.ApplicationSession;
import org.example.util.SceneNavigator;

public class LoginController {
    private final AuctionApiClient apiClient = AuctionApiClient.getInstance();
    private final AuthenticationService authenticationService = AuthenticationService.getInstance();
    private final ApplicationSession applicationSession = ApplicationSession.getInstance();

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

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
            boolean usedLocalFallback = authenticate(username, password);
            if (usedLocalFallback) {
                showAlert(
                        Alert.AlertType.INFORMATION,
                        "API unavailable",
                        "Signed in with local data because the configured API server could not be reached."
                );
            }
            SceneNavigator.switchScene(usernameField, "/view/Dashboard.fxml", "Auction Dashboard");
        } catch (UserNotFound e) {
            showAlert(Alert.AlertType.WARNING, "User not found", e.getMessage());
        } catch (InvalidPasswordException e) {
            showAlert(Alert.AlertType.WARNING, "Password incorrect", e.getMessage());
        } catch (AuctionApiClient.ApiClientException e) {
            showAlert(Alert.AlertType.WARNING, "Server login failed", e.getMessage());
        } catch (RuntimeException e) {
            applicationSession.logout();
            showAlert(Alert.AlertType.WARNING, "Dashboard unavailable", failureMessage(e));
        }
    }

    @FXML
    private void handleOpenRegistration() {
        SceneNavigator.switchScene(usernameField, "/view/Register.fxml", "Create Account");
    }

    private boolean authenticate(String username, String password) throws UserNotFound, InvalidPasswordException {
        if (!apiClient.isEnabled()) {
            applicationSession.login(authenticationService.loginOrThrow(username, password));
            return false;
        }

        try {
            AuctionApiClient.AuthResult result = apiClient.login(username, password);
            applicationSession.login(result.user(), result.token());
            return false;
        } catch (AuctionApiClient.ApiClientException e) {
            if (!AuctionApiClient.isConnectivityFailure(e)) {
                throw e;
            }
            var localUser = authenticationService.loginOrThrow(username, password);
            applicationSession.login(localUser);
            return true;
        }
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
