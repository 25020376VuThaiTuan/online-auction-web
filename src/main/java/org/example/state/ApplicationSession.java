package org.example.state;

import org.example.model.User;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ApplicationSession {
    private static final ApplicationSession INSTANCE = new ApplicationSession();

    private User currentUser;
    private String apiToken;
    private String selectedAuctionId;
    private String trustedWalletUserId;
    private String trustedWalletAuthorizationToken;
    private LocalDateTime trustedWalletAuthorizationExpiresAt;
    private final Set<String> shownNotificationPopupKeys = new HashSet<>();
    private final Set<String> watchedAuctionIds = new HashSet<>();

    private ApplicationSession() {
    }

    public static ApplicationSession getInstance() {
        return INSTANCE;
    }

    public void login(User user) {
        login(user, null);
    }

    public void login(User user, String apiToken) {
        currentUser = Objects.requireNonNull(user, "user");
        this.apiToken = apiToken;
        selectedAuctionId = null;
        clearTrustedWalletAuthorization();
        watchedAuctionIds.clear();
    }

    public void replaceCurrentUser(User user) {
        currentUser = Objects.requireNonNull(user, "user");
    }

    public void logout() {
        currentUser = null;
        apiToken = null;
        selectedAuctionId = null;
        clearTrustedWalletAuthorization();
        watchedAuctionIds.clear();
    }

    public Optional<User> getCurrentUser() {
        return Optional.ofNullable(currentUser);
    }

    public Optional<String> getApiToken() {
        return Optional.ofNullable(apiToken);
    }

    public Optional<String> getSelectedAuctionId() {
        return Optional.ofNullable(selectedAuctionId);
    }

    public void setSelectedAuctionId(String selectedAuctionId) {
        this.selectedAuctionId = selectedAuctionId;
    }

    public boolean toggleWatchedAuction(String auctionId) {
        String normalizedAuctionId = normalizeAuctionId(auctionId);
        if (normalizedAuctionId.isBlank()) {
            return false;
        }
        if (watchedAuctionIds.add(normalizedAuctionId)) {
            return true;
        }
        watchedAuctionIds.remove(normalizedAuctionId);
        return false;
    }

    public void watchAuction(String auctionId) {
        String normalizedAuctionId = normalizeAuctionId(auctionId);
        if (!normalizedAuctionId.isBlank()) {
            watchedAuctionIds.add(normalizedAuctionId);
        }
    }

    public void unwatchAuction(String auctionId) {
        watchedAuctionIds.remove(normalizeAuctionId(auctionId));
    }

    public void clearWatchedAuctions() {
        watchedAuctionIds.clear();
    }

    public boolean isAuctionWatched(String auctionId) {
        return watchedAuctionIds.contains(normalizeAuctionId(auctionId));
    }

    public int watchedAuctionCount() {
        return watchedAuctionIds.size();
    }

    public boolean rememberNotificationPopup(String notificationKey) {
        if (notificationKey == null || notificationKey.isBlank()) {
            return false;
        }
        return shownNotificationPopupKeys.add(notificationKey.trim());
    }

    public void trustWalletAuthorization(String userId, String authorizationToken, LocalDateTime expiresAt) {
        if (userId == null || userId.isBlank()
                || authorizationToken == null || authorizationToken.isBlank()
                || expiresAt == null
                || expiresAt.isBefore(LocalDateTime.now())) {
            clearTrustedWalletAuthorization();
            return;
        }
        trustedWalletUserId = userId;
        trustedWalletAuthorizationToken = authorizationToken;
        trustedWalletAuthorizationExpiresAt = expiresAt;
    }

    public void trustWalletAuthorization(String userId, String authorizationToken, Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            clearTrustedWalletAuthorization();
            return;
        }
        trustWalletAuthorization(userId, authorizationToken, LocalDateTime.now().plus(duration));
    }

    public Optional<String> getTrustedWalletAuthorization(String userId) {
        if (trustedWalletUserId == null
                || trustedWalletAuthorizationToken == null
                || trustedWalletAuthorizationExpiresAt == null
                || !trustedWalletUserId.equals(userId)
                || trustedWalletAuthorizationExpiresAt.isBefore(LocalDateTime.now())) {
            clearTrustedWalletAuthorization();
            return Optional.empty();
        }
        return Optional.of(trustedWalletAuthorizationToken);
    }

    public LocalDateTime getTrustedWalletAuthorizationExpiresAt() {
        return trustedWalletAuthorizationExpiresAt;
    }

    public void clearTrustedWalletAuthorization() {
        trustedWalletUserId = null;
        trustedWalletAuthorizationToken = null;
        trustedWalletAuthorizationExpiresAt = null;
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

    private String normalizeAuctionId(String auctionId) {
        return auctionId == null ? "" : auctionId.trim();
    }
}
