package org.example.state;

import org.example.model.Bidder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
