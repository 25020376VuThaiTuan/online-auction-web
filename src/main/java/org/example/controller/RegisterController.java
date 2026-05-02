package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.example.service.MarketplaceDashboardService;
import org.example.state.ApplicationSession;
import org.example.util.SceneNavigator;

public class RegisterController {
    private final MarketplaceDashboardService dashboardService = MarketplaceDashboardService.getInstance();
    private final ApplicationSession applicationSession = ApplicationSession.getInstance();

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
    private void handleRegister() {
        String fullName = value(fullNameField.getText());
        String username = value(usernameField.getText());
        String email = value(emailField.getText());
        String password = value(passwordField.getText());
        String confirmPassword = value(confirmPasswordField.getText());

        if (username.isBlank() || password.isBlank() || email.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Missing fields", "Username, email, and password are required.");
            return;
        }
        if (!password.equals(confirmPassword)) {
            showAlert(Alert.AlertType.WARNING, "Password mismatch", "Confirm password must match the password.");
            return;
        }

        try {
            applicationSession.login(dashboardService.registerManualBidder(username, password, email, fullName));
            SceneNavigator.switchScene(usernameField, "/view/Dashboard.fxml", "Auction Dashboard");
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

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
