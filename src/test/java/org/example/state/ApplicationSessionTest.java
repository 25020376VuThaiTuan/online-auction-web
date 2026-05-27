package org.example.state;

import org.example.model.Bidder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationSessionTest {
    private final ApplicationSession session = ApplicationSession.getInstance();

    @BeforeEach
    void resetBeforeTest() {
        session.logout();
    }

    @AfterEach
    void resetAfterTest() {
        session.logout();
    }

    @Test
    void watchlistTogglesAndNormalizesAuctionIds() {
        assertFalse(session.isAuctionWatched("ITEM-1"));

        assertTrue(session.toggleWatchedAuction(" ITEM-1 "));
        assertTrue(session.isAuctionWatched("ITEM-1"));
        assertEquals(1, session.watchedAuctionCount());

        assertFalse(session.toggleWatchedAuction("ITEM-1"));
        assertFalse(session.isAuctionWatched("ITEM-1"));
        assertEquals(0, session.watchedAuctionCount());
    }

    @Test
    void blankAuctionIdsAreIgnored() {
        assertFalse(session.toggleWatchedAuction(" "));
        session.watchAuction(null);

        assertEquals(0, session.watchedAuctionCount());
    }

    @Test
    void watchedAuctionsCanBeClearedInOneOperation() {
        session.watchAuction("ITEM-1");
        session.watchAuction("ITEM-2");

        session.clearWatchedAuctions();

        assertEquals(0, session.watchedAuctionCount());
        assertFalse(session.isAuctionWatched("ITEM-1"));
        assertFalse(session.isAuctionWatched("ITEM-2"));
    }

    @Test
    void watchlistClearsWhenSessionChanges() {
        Bidder user = new Bidder("U-SESSION-1", "session-user", "secret", "session@example.test", 100.0);

        session.login(user);
        session.watchAuction("ITEM-1");
        assertTrue(session.isAuctionWatched("ITEM-1"));

        session.login(user);
        assertFalse(session.isAuctionWatched("ITEM-1"));

        session.watchAuction("ITEM-2");
        session.logout();
        assertFalse(session.isAuctionWatched("ITEM-2"));
    }

    @Test
    void notificationPopupKeysResetWhenSessionChanges() {
        Bidder user = new Bidder("U-SESSION-2", "session-popup-user", "secret", "session-popup@example.test", 100.0);
        String key = "notification-" + UUID.randomUUID();

        assertTrue(session.rememberNotificationPopup(key));
        assertFalse(session.rememberNotificationPopup(" " + key + " "));

        session.login(user);
        assertTrue(session.rememberNotificationPopup(key));
        assertFalse(session.rememberNotificationPopup(key));

        session.logout();
        assertTrue(session.rememberNotificationPopup(key));
    }

    @Test
    void notificationPopupKeysStayDedupedWhenCurrentUserIsRefreshed() {
        Bidder user = new Bidder("U-SESSION-POPUP", "session-popup-user", "secret", "session-popup@example.test", 100.0);
        Bidder refreshedUser = new Bidder("U-SESSION-POPUP", "session-popup-user", "secret", "session-popup@example.test", 125.0);
        String key = "notification-" + UUID.randomUUID();

        session.login(user);
        assertTrue(session.rememberNotificationPopup(key));

        session.replaceCurrentUser(refreshedUser);

        assertFalse(session.rememberNotificationPopup(key));
    }

    @Test
    void loginTracksApiTokenSelectionAndUserLabel() {
        Bidder user = new Bidder("U-SESSION-3", "session-label-user", "secret", "session-label@example.test", 100.0);

        assertEquals("Guest", session.getCurrentUserLabel());

        session.login(user, "api-token-1");
        session.setSelectedAuctionId("ITEM-SELECTED");

        assertEquals("api-token-1", session.getApiToken().orElseThrow());
        assertEquals("ITEM-SELECTED", session.getSelectedAuctionId().orElseThrow());
        assertEquals("session-label-user (USER)", session.getCurrentUserLabel());

        user.setRole("bidder");
        session.replaceCurrentUser(user);

        assertEquals("session-label-user (BIDDER)", session.getCurrentUserLabel());
        assertThrows(NullPointerException.class, () -> session.replaceCurrentUser(null));
    }

    @Test
    void trustedWalletAuthorizationRequiresMatchingUnexpiredUserToken() {
        session.trustWalletAuthorization("user-1", "token-1", Duration.ofMinutes(5));

        assertEquals("token-1", session.getTrustedWalletAuthorization("user-1").orElseThrow());
        assertTrue(session.getTrustedWalletAuthorizationExpiresAt().isAfter(LocalDateTime.now()));
        assertTrue(session.getTrustedWalletAuthorization("user-2").isEmpty());
        assertNull(session.getTrustedWalletAuthorizationExpiresAt());
    }

    @Test
    void invalidTrustedWalletAuthorizationClearsStoredToken() {
        session.trustWalletAuthorization("user-1", "token-1", Duration.ofMinutes(5));

        session.trustWalletAuthorization("user-1", "token-1", LocalDateTime.now().minusSeconds(1));

        assertTrue(session.getTrustedWalletAuthorization("user-1").isEmpty());
        assertNull(session.getTrustedWalletAuthorizationExpiresAt());

        session.trustWalletAuthorization("user-1", "token-1", Duration.ZERO);
        assertTrue(session.getTrustedWalletAuthorization("user-1").isEmpty());

        session.trustWalletAuthorization(" ", "token-1", Duration.ofMinutes(5));
        assertTrue(session.getTrustedWalletAuthorization("user-1").isEmpty());
    }

    @Test
    void auctionsCanBeUnwatchedDirectly() {
        session.watchAuction(" ITEM-DIRECT ");

        session.unwatchAuction("ITEM-DIRECT");

        assertFalse(session.isAuctionWatched("ITEM-DIRECT"));
    }
}
