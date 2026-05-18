package org.example.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.example.client.AuctionApiClient;
import org.example.service.MarketplaceDashboardService;
import org.example.state.ApplicationSession;
import org.example.util.SceneNavigator;

public class RegisterController {
    private final AuctionApiClient apiClient = AuctionApiClient.getInstance();
    private final MarketplaceDashboardService dashboardService = MarketplaceDashboardService.getInstance();
    private final ApplicationSession applicationSession = ApplicationSession.getInstance();

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
            if (apiClient.isEnabled()) {
                AuctionApiClient.AuthResult result = "SELLER".equalsIgnoreCase(accountRole)
                        ? apiClient.registerManualSeller(username, password, email, fullName)
                        : apiClient.registerManualBidder(username, password, email, fullName);
                applicationSession.login(result.user(), result.token());
            } else {
                applicationSession.login("SELLER".equalsIgnoreCase(accountRole)
                        ? dashboardService.registerManualSeller(username, password, email, fullName)
                        : dashboardService.registerManualBidder(username, password, email, fullName));
            }
            SceneNavigator.switchScene(usernameField, "/view/Dashboard.fxml", "Auction Dashboard");
        } catch (IllegalArgumentException | AuctionApiClient.ApiClientException e) {
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
