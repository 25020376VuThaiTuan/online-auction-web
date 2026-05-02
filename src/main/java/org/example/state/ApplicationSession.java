package org.example.state;

import org.example.model.User;

import java.util.Objects;
import java.util.Optional;

public final class ApplicationSession {
    private static final ApplicationSession INSTANCE = new ApplicationSession();

    private User currentUser;
    private String selectedAuctionId;

    private ApplicationSession() {
    }

    public static ApplicationSession getInstance() {
        return INSTANCE;
    }

    public void login(User user) {
        currentUser = Objects.requireNonNull(user, "user");
        selectedAuctionId = null;
    }

    public void logout() {
        currentUser = null;
        selectedAuctionId = null;
    }

    public Optional<User> getCurrentUser() {
        return Optional.ofNullable(currentUser);
    }

    public Optional<String> getSelectedAuctionId() {
        return Optional.ofNullable(selectedAuctionId);
    }

    public void setSelectedAuctionId(String selectedAuctionId) {
        this.selectedAuctionId = selectedAuctionId;
    }

    public String getCurrentUserLabel() {
        if (currentUser == null) {
            return "Guest";
        }

        String role = currentUser.getRole() == null || currentUser.getRole().isBlank()
                ? "USER"
                : currentUser.getRole();
        return currentUser.getUsername() + " (" + role + ")";
    }
}
