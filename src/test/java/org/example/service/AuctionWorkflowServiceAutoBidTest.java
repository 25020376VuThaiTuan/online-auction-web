package org.example.service;

import org.example.auction.AuctionStatus;
import org.example.auction.AuctionSummary;
import org.example.auction.BidValidationResult;
import org.example.model.AutoBid;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.model.ItemFactory;
import org.example.model.Seller;
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

    @Test
    void itemCreatorCannotBidEvenWithDepositAndBalance() {
        Item item = item("ITEM-CREATOR-BLOCK");
        Bidder creator = new Bidder("creator-user", "creator", "secret", "creator@example.test", 500.0);
        creator.setRole("BIDDER");
        item.setSellerId(creator.getId());
        AuctionSummary summary = runningSummary(item);

        BidValidationResult result = AuctionWorkflowService.bidAuthorizationFailure(
                item,
                summary,
                creator,
                60.0,
                true,
                creator.getAvailableBalance()
        );

        assertNotNull(result);
        assertTrue(result.message().contains("creators cannot bid"));
    }

    @Test
    void nonCreatorSellerCanBidAfterConfirmingEntryDeposit() {
        Item item = item("ITEM-SELLER-CAN-BID");
        item.setSellerId("creator-user");
        Seller seller = new Seller("seller-user", "seller", "secret", "seller@example.test");
        seller.setRole("SELLER");

        assertNull(AuctionWorkflowService.bidAuthorizationFailure(
                item,
                runningSummary(item),
                seller,
                60.0,
                true,
                500.0
        ));
    }

    private Item item(String id) {
        return ItemFactory.createItem(
                "electronics",
                id,
                "Authorization Test",
                "Test item",
                40.0,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(10),
                "Brand",
                1
        );
    }

    private AuctionSummary runningSummary(Item item) {
        return new AuctionSummary(
                item.getId(),
                item.getItemName(),
                AuctionStatus.RUNNING,
                item.getCurrentPrice(),
                50.0,
                600L,
                0,
                null
        );
    }
}
