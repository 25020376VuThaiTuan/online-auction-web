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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionRulesCoverageTest {
    @Test
    void statusAndRemainingTimeHandleNullFutureRunningAndFinishedStates() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 27, 9, 0);

        assertEquals(AuctionStatus.RUNNING, AuctionRules.resolveStatus(null, null, now));
        assertEquals(AuctionStatus.OPEN, AuctionRules.resolveStatus(now.plusMinutes(1), now.plusHours(1), now));
        assertEquals(AuctionStatus.RUNNING, AuctionRules.resolveStatus(now.minusMinutes(1), now.plusHours(1), now));
        assertEquals(AuctionStatus.FINISHED, AuctionRules.resolveStatus(now.minusHours(1), now, now));
        assertEquals(Long.MAX_VALUE, AuctionRules.remainingSeconds(null, now));
        assertEquals(0L, AuctionRules.remainingSeconds(now.minusSeconds(1), now));
        assertEquals(60L, AuctionRules.remainingSeconds(now.plusMinutes(1), now));
    }

    @Test
    void priceRulesUseExpectedIncrementTiersAndDeposits() {
        assertEquals(10.0, AuctionRules.minimumIncrement(-1.0), 0.001);
        assertEquals(10.0, AuctionRules.minimumIncrement(999.99), 0.001);
        assertEquals(50.0, AuctionRules.minimumIncrement(1_000.0), 0.001);
        assertEquals(100.0, AuctionRules.minimumIncrement(5_000.0), 0.001);
        assertEquals(250.0, AuctionRules.minimumIncrement(10_000.0), 0.001);
        assertEquals(500.0, AuctionRules.minimumIncrement(50_000.0), 0.001);
        assertEquals(1_050.0, AuctionRules.minimumNextBid(1_000.0), 0.001);
        assertEquals(25.0, AuctionRules.requiredDeposit(10.0), 0.001);
        assertEquals(210.0, AuctionRules.requiredDeposit(1_000.0), 0.001);
    }

    @Test
    void validateBidRejectsInvalidAuctionStatesAndAmounts() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 27, 9, 0);
        Item future = item("future", now.plusMinutes(1), now.plusHours(1), 100.0);
        Item finished = item("finished", now.minusHours(2), now.minusHours(1), 100.0);
        Item running = item("running", now.minusMinutes(5), now.plusHours(1), 100.0);

        assertThrows(NullPointerException.class, () -> AuctionRules.validateBid(null, 120.0, now));
        assertRejected("positive number", AuctionRules.validateBid(running, Double.NaN, now));
        assertRejected("positive number", AuctionRules.validateBid(running, 0.0, now));
        assertRejected("not started", AuctionRules.validateBid(future, 120.0, now));
        assertRejected("no longer", AuctionRules.validateBid(finished, 120.0, now));
        assertRejected("too low", AuctionRules.validateBid(running, 109.0, now));
    }

    @Test
    void validateBidAcceptsAndExtendsWhenBidLandsInsideTriggerWindow() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 27, 9, 0);
        Item running = item("running", now.minusMinutes(5), now.plusSeconds(30), 100.0);

        BidValidationResult result = AuctionRules.validateBid(running, 120.0, now, null);

        assertTrue(result.accepted());
        assertEquals("Bid accepted. Auction end time was extended.", result.message());
        assertEquals(now.plusSeconds(90), result.effectiveEndTime());
    }

    @Test
    void extensionCalculationReturnsOriginalEndWhenInputsCannotExtend() {
        LocalDateTime bidTime = LocalDateTime.of(2026, 5, 27, 9, 0);
        LocalDateTime endTime = bidTime.plusMinutes(5);

        assertNull(AuctionRules.calculateExtendedEndTime(null, bidTime, 60L, 60L));
        assertSame(endTime, AuctionRules.calculateExtendedEndTime(endTime, null, 60L, 60L));
        assertSame(endTime, AuctionRules.calculateExtendedEndTime(endTime, bidTime, 60L, 0L));
        assertSame(endTime, AuctionRules.calculateExtendedEndTime(endTime, bidTime, 60L, 60L));
        assertEquals(endTime.plusSeconds(60), AuctionRules.calculateExtendedEndTime(endTime, endTime, 60L, 60L));
    }

    @Test
    void buildSummaryUsesLastBidAsHighestBidderAndHandlesNullBidHistory() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 27, 9, 0);
        Item running = item("summary", now.minusMinutes(5), now.plusMinutes(10), 100.0);

        AuctionSummary emptySummary = AuctionRules.buildSummary(running, null, AuctionStatus.RUNNING, now);
        assertEquals(0, emptySummary.totalBids());
        assertNull(emptySummary.highestBidderId());
        assertEquals(600L, emptySummary.secondsRemaining());

        List<Bid> bids = List.of(
                new Bid("BID-1", "BIDDER-1", running.getId(), 120.0, now.minusMinutes(1)),
                new Bid("BID-2", "BIDDER-2", running.getId(), 140.0, now)
        );
        AuctionSummary summary = AuctionRules.buildSummary(running, bids, AuctionStatus.RUNNING, now);

        assertEquals(running.getId(), summary.itemId());
        assertEquals("Item summary", summary.itemName());
        assertEquals(2, summary.totalBids());
        assertEquals("BIDDER-2", summary.highestBidderId());
        assertFalse(summary.status().isFinished());
    }

    private static void assertRejected(String expectedMessagePart, BidValidationResult result) {
        assertFalse(result.accepted());
        assertTrue(result.message().contains(expectedMessagePart));
    }

    private static Item item(String idSuffix, LocalDateTime start, LocalDateTime end, double price) {
        return ItemFactory.createItem(
                "electronics",
                "ITEM-" + idSuffix + "-" + UUID.randomUUID(),
                "Item " + idSuffix,
                "Description",
                price,
                start,
                end,
                "Brand",
                12
        );
    }
}
