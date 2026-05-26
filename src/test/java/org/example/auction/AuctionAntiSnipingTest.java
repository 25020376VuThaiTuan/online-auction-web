package org.example.auction;

import org.example.model.Bid;
import org.example.model.Item;
import org.example.model.ItemFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionAntiSnipingTest {
    @Test
    void lateBidInsideTriggerWindowExtendsAuctionByDefaultWindow() {
        LocalDateTime endTime = LocalDateTime.of(2026, 5, 14, 12, 0, 0);
        LocalDateTime bidTime = endTime.minusSeconds(AuctionRules.DEFAULT_EXTENSION_TRIGGER_SECONDS);

        LocalDateTime extendedEndTime = AuctionRules.calculateExtendedEndTime(
                endTime,
                bidTime,
                AuctionRules.DEFAULT_EXTENSION_TRIGGER_SECONDS,
                AuctionRules.DEFAULT_EXTENSION_SECONDS
        );

        assertEquals(endTime.plusSeconds(AuctionRules.DEFAULT_EXTENSION_SECONDS), extendedEndTime);
    }

    @Test
    void bidOutsideTriggerWindowKeepsOriginalEndTime() {
        LocalDateTime endTime = LocalDateTime.of(2026, 5, 14, 12, 0, 0);
        LocalDateTime bidTime = endTime.minusSeconds(AuctionRules.DEFAULT_EXTENSION_TRIGGER_SECONDS + 1);

        LocalDateTime extendedEndTime = AuctionRules.calculateExtendedEndTime(
                endTime,
                bidTime,
                AuctionRules.DEFAULT_EXTENSION_TRIGGER_SECONDS,
                AuctionRules.DEFAULT_EXTENSION_SECONDS
        );

        assertEquals(endTime, extendedEndTime);
    }

    @Test
    void acceptedLateSessionBidUpdatesSessionAndItemEndTime() {
        LocalDateTime originalEndTime = LocalDateTime.now().plusSeconds(10);
        Item item = runningItem(originalEndTime);
        AuctionSession session = new AuctionSession(item, item.getStartingPrice(), item.getEndTime());

        BidValidationResult result = session.submitBid(bid(item.getId(), 110.0));

        LocalDateTime expectedEndTime = originalEndTime.plusSeconds(AuctionRules.DEFAULT_EXTENSION_SECONDS);
        assertTrue(result.accepted());
        assertTrue(result.message().contains("extended"));
        assertEquals(expectedEndTime, result.effectiveEndTime());
        assertEquals(expectedEndTime, session.getEndTime());
        assertEquals(expectedEndTime, item.getEndTime());
    }

    @Test
    void acceptedLateSessionBidUsesConfiguredExtensionWindow() {
        LocalDateTime originalEndTime = LocalDateTime.now().plusSeconds(20);
        Item item = runningItem(originalEndTime);
        AuctionSession session = new AuctionSession(
                item,
                item.getStartingPrice(),
                item.getEndTime(),
                List.of(),
                new AuctionExtensionConfig(30, 120, 0, 10)
        );

        BidValidationResult result = session.submitBid(bid(item.getId(), 110.0));

        LocalDateTime expectedEndTime = originalEndTime.plusSeconds(120);
        assertTrue(result.accepted());
        assertEquals(expectedEndTime, result.effectiveEndTime());
        assertEquals(1, session.getExtensionCount());
    }

    @Test
    void acceptedLateSessionBidExtendsEvenAfterLegacyMaxExtensions() {
        LocalDateTime originalEndTime = LocalDateTime.now().plusSeconds(10);
        Item item = runningItem(originalEndTime);
        AuctionSession session = new AuctionSession(
                item,
                item.getStartingPrice(),
                item.getEndTime(),
                List.of(),
                new AuctionExtensionConfig(
                        AuctionRules.DEFAULT_EXTENSION_TRIGGER_SECONDS,
                        AuctionRules.DEFAULT_EXTENSION_SECONDS,
                        10,
                        10
                )
        );

        BidValidationResult result = session.submitBid(bid(item.getId(), 110.0));

        LocalDateTime expectedEndTime = originalEndTime.plusSeconds(AuctionRules.DEFAULT_EXTENSION_SECONDS);
        assertTrue(result.accepted());
        assertEquals(expectedEndTime, result.effectiveEndTime());
        assertEquals(expectedEndTime, session.getEndTime());
        assertEquals(11, session.getExtensionCount());
    }

    @Test
    void rejectedLateSessionBidDoesNotExtendAuction() {
        LocalDateTime originalEndTime = LocalDateTime.now().plusSeconds(10);
        Item item = runningItem(originalEndTime);
        AuctionSession session = new AuctionSession(item, item.getStartingPrice(), item.getEndTime());

        BidValidationResult result = session.submitBid(bid(item.getId(), 100.0));

        assertFalse(result.accepted());
        assertEquals(originalEndTime, session.getEndTime());
        assertEquals(originalEndTime, item.getEndTime());
    }

    private Item runningItem(LocalDateTime endTime) {
        return ItemFactory.createItem(
                "electronics",
                "SNIP-" + UUID.randomUUID().toString().substring(0, 8),
                "Anti-Sniping Test",
                "Test item",
                100.0,
                LocalDateTime.now().minusMinutes(1),
                endTime,
                "Brand",
                12
        );
    }

    private Bid bid(String itemId, double amount) {
        return new Bid("BID-" + UUID.randomUUID(), "bidder-test", itemId, amount, LocalDateTime.now());
    }
}
