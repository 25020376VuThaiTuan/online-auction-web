package org.example.service;

import org.example.auction.AuctionStatus;
import org.example.auction.AuctionSummary;
import org.example.auction.BidValidationResult;
import org.example.model.AutoBid;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.model.ItemFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionWorkflowServiceAutoBidTest {
    @Test
    void autoBidUsesConfiguredIncrementWhenItClearsMinimumBid() {
        AutoBid autoBid = new AutoBid(1, "bidder-auto", "ITEM-1", 200.0, 25.0);

        assertEquals(125.0, AuctionWorkflowService.nextAutoBidAmount(100.0, autoBid), 0.001);
    }

    @Test
    void autoBidDoesNotTriggerForTheBidderWhoPlacedTheLatestBid() {
        AutoBid autoBid = new AutoBid(1, "bidder-auto", "ITEM-1", 200.0, 25.0);

        assertFalse(AuctionWorkflowService.shouldTriggerAutoBid(autoBid, "bidder-auto", "bidder-auto"));
        assertTrue(AuctionWorkflowService.shouldTriggerAutoBid(autoBid, "bidder-other", "bidder-other"));
    }

    @Test
    void autoBidSkipsWhenMaxLimitCannotReachMinimumNextBid() {
        AutoBid autoBid = new AutoBid(1, "bidder-auto", "ITEM-1", 105.0, 25.0);

        assertEquals(0.0, AuctionWorkflowService.nextAutoBidAmount(100.0, autoBid), 0.001);
    }

    @Test
    void bidsUseAvailableBalanceAfterEntryDepositInsteadOfTotalWalletBalance() {
        Item item = ItemFactory.createItem(
                "electronics",
                "ITEM-LOCKED-BALANCE",
                "Locked Balance Test",
                "Test item",
                40.0,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(10),
                "Brand",
                1
        );
        Bidder bidder = new Bidder("bidder-locked", "locked", "secret", "locked@example.test", 150.0);
        bidder.lockDeposit(item.getId(), 100.0);
        AuctionSummary summary = new AuctionSummary(
                item.getId(),
                item.getItemName(),
                AuctionStatus.RUNNING,
                item.getCurrentPrice(),
                50.0,
                600L,
                0,
                null
        );

        BidValidationResult overAvailable = AuctionWorkflowService.bidAuthorizationFailure(
                item,
                summary,
                bidder,
                60.0,
                true,
                bidder.getAvailableBalance()
        );
        AutoBid autoBid = new AutoBid(1, bidder.getId(), item.getId(), bidder.getBalance(), 10.0);

        assertNotNull(overAvailable);
        assertTrue(overAvailable.message().contains("Available balance"));
        assertNull(AuctionWorkflowService.bidAuthorizationFailure(
                item,
                summary,
                bidder,
                50.0,
                true,
                bidder.getAvailableBalance()
        ));
        assertEquals(50.0, AuctionWorkflowService.nextAutoBidAmount(40.0, autoBid, bidder.getAvailableBalance()), 0.001);
        assertEquals(0.0, AuctionWorkflowService.nextAutoBidAmount(45.0, autoBid, bidder.getAvailableBalance()), 0.001);
    }
}
