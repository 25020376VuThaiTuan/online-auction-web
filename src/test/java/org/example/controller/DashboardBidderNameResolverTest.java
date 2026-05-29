package org.example.controller;

import org.example.model.Bidder;
import org.example.service.MarketplaceDashboardService;
import org.example.state.ApplicationSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DashboardBidderNameResolverTest {
    private final ApplicationSession session = ApplicationSession.getInstance();

    @AfterEach
    void clearSession() {
        session.logout();
    }

    @Test
    void displayNameHandlesBlankCurrentUserLookupFallbackAndLookupFailures() {
        DashboardBidderNameResolver resolver = new DashboardBidderNameResolver(
                session,
                MarketplaceDashboardService.getInstance()
        );

        assertEquals("Unknown bidder", resolver.displayName(" "));

        Bidder bidder = new Bidder("bidder-name-1", "bidderName", "hash", "bidder@test.local", 10.0);
        bidder.setFullName("Bidder Name");
        session.login(bidder);
        assertEquals("Bidder Name", resolver.displayName("bidder-name-1"));
        assertEquals("missing-bidder", resolver.displayName("missing-bidder"));

        DashboardBidderNameResolver failingResolver = new DashboardBidderNameResolver(session, null);
        assertEquals("other-bidder", failingResolver.displayName("other-bidder"));
    }
}
