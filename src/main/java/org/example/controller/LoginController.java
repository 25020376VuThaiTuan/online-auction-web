package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.example.exception.InvalidPasswordException;
import org.example.exception.UserNotFound;
import org.example.service.AuthenticationService;
import org.example.state.ApplicationSession;
import org.example.util.SceneNavigator;

public class LoginController {
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
        hintLabel.setText(authenticationService.getLoginHint());
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
            // Authentication problems are surfaced as explicit exceptions so the UI can explain the reason.
            applicationSession.login(authenticationService.loginOrThrow(username, password));
            SceneNavigator.switchScene(usernameField, "/view/AuctionList.fxml", "Auction Catalog");
        } catch (UserNotFound e) {
            showAlert("User not found", e.getMessage());
        } catch (InvalidPasswordException e) {
            showAlert("Password incorrect", e.getMessage());
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
