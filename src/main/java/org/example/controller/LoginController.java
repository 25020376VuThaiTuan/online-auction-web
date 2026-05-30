package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.example.client.AuctionApiClient;
import org.example.exception.InvalidPasswordException;
import org.example.exception.UserNotFound;
import org.example.service.AuthenticationService;
import org.example.state.ApplicationSession;
import org.example.util.SceneNavigator;

import java.util.Optional;

public class LoginController {
    private final AuctionApiClient apiClient;
    private final AuthenticationService authenticationService;
    private final ApplicationSession applicationSession;

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
    private void handleOpenRegistration() {
        SceneNavigator.switchScene(usernameField, "/view/Register.fxml", "Create Account");
    }

    @FXML
    private void handleForgotPassword() {
        Dialog<PasswordResetInput> dialog = new Dialog<>();
        dialog.setTitle("Reset password");
        dialog.setHeaderText(null);

        TextField dialogUsernameField = new TextField(usernameField.getText());
        dialogUsernameField.setPromptText("Username");
        TextField emailField = new TextField();
        emailField.setPromptText("Account email");
        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("New password");
        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm new password");

        GridPane form = new GridPane();
        form.setHgap(10.0);
        form.setVgap(10.0);
        form.addRow(0, new Label("Username"), dialogUsernameField);
        form.addRow(1, new Label("Email"), emailField);
        form.addRow(2, new Label("New password"), newPasswordField);
        form.addRow(3, new Label("Confirm password"), confirmPasswordField);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(buttonType -> buttonType == ButtonType.OK
                ? new PasswordResetInput(
                        dialogUsernameField.getText(),
                        emailField.getText(),
                        newPasswordField.getText(),
                        confirmPasswordField.getText()
                )
                : null);

        Optional<PasswordResetInput> result = dialog.showAndWait();
        result.ifPresent(input -> {
            try {
                resetPassword(input.username(), input.email(), input.newPassword(), input.confirmPassword());
                showAlert(Alert.AlertType.INFORMATION, "Password reset", "Sign in with your new password.");
            } catch (UserNotFound e) {
                showAlert(Alert.AlertType.WARNING, "Account not found", e.getMessage());
            } catch (AuctionApiClient.ApiClientException e) {
                showAlert(
                        Alert.AlertType.WARNING,
                        AuctionApiClient.isConnectivityFailure(e) ? "API unavailable" : "Password reset failed",
                        e.getMessage()
                );
            } catch (IllegalArgumentException | IllegalStateException e) {
                showAlert(Alert.AlertType.WARNING, "Password reset failed", e.getMessage());
            }
        });
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

    void resetPassword(String username, String email, String newPassword, String confirmPassword) throws UserNotFound {
        if (!apiClient.isEnabled()) {
            authenticationService.resetPassword(username, email, newPassword, confirmPassword);
            return;
        }
        apiClient.resetPassword(username, email, newPassword, confirmPassword);
    }

    private AuctionApiClient.ApiClientException configuredApiUnavailable(AuctionApiClient.ApiClientException cause) {
        return new AuctionApiClient.ApiClientException(
                "Could not reach the configured auction API server. Check AUCTION_API_BASE_URL and make sure the "
                        + "API server is running, then try signing in again. Local demo sign-in is used only when "
                        + "AUCTION_API_BASE_URL is not set.",
                cause
        );
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

    private record PasswordResetInput(
            String username,
            String email,
            String newPassword,
            String confirmPassword
    ) {
    }
}
