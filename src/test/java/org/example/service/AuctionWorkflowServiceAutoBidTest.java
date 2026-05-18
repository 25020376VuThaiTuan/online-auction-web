package org.example.service;

import org.example.model.AutoBid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
