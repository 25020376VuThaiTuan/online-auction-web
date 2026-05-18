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
            showAlert("Missing credentials", "Enter both username and password.");
            return;
        }

        try {
            if (apiClient.isEnabled()) {
                AuctionApiClient.AuthResult result = apiClient.login(username, password);
                applicationSession.login(result.user(), result.token());
            } else {
                applicationSession.login(authenticationService.loginOrThrow(username, password));
            }
            SceneNavigator.switchScene(usernameField, "/view/Dashboard.fxml", "Auction Dashboard");
        } catch (UserNotFound e) {
            showAlert("User not found", e.getMessage());
        } catch (InvalidPasswordException e) {
            showAlert("Password incorrect", e.getMessage());
        } catch (AuctionApiClient.ApiClientException e) {
            showAlert("Server login failed", e.getMessage());
        } catch (RuntimeException e) {
            applicationSession.logout();
            showAlert("Dashboard unavailable", failureMessage(e));
        }
    }

    @FXML
    private void handleOpenRegistration() {
        SceneNavigator.switchScene(usernameField, "/view/Register.fxml", "Create Account");
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
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
