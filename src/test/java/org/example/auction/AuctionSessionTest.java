package org.example.auction;

import org.example.model.Bid;
import org.example.model.Item;
import org.example.model.ItemFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuctionSessionTest {

    private Item item;
    private AuctionSession session;

    @BeforeEach
    void setUp() {
        item = ItemFactory.createItem(
                "electronics",
                "ITEM-" + UUID.randomUUID().toString().substring(0, 8),
                "Gaming Laptop",
                "High-end gaming laptop",
                100.0,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plusMinutes(30),
                "Asus",
                24
        );

        session = new AuctionSession(
                item,
                item.getStartingPrice(),
                item.getEndTime()
        );
    }

    @Test
    void constructorShouldInitializeSessionCorrectly() {
        assertEquals(item, session.getItem());
        assertEquals(100.0, session.getCurrentHighestBid());
        assertEquals(item.getEndTime(), session.getEndTime());
        assertEquals(AuctionStatus.RUNNING, session.getStatus());
        assertTrue(session.getBids().isEmpty());
    }

    @Test
    void submitBidShouldAcceptValidBid() {
        Bid bid = createBid("bidder-1", item.getId(), 120.0);

        BidValidationResult result = session.submitBid(bid);

        assertTrue(result.accepted());
        assertEquals(120.0, session.getCurrentHighestBid());
        assertEquals(1, session.getBids().size());
        assertEquals(120.0, item.getCurrentPrice());
    }

    @Test
    void submitBidShouldRejectLowerBid() {
        session.submitBid(createBid("bidder-1", item.getId(), 120.0));

        BidValidationResult result = session.submitBid(
                createBid("bidder-2", item.getId(), 110.0)
        );

        assertFalse(result.accepted());
        assertEquals(120.0, session.getCurrentHighestBid());
        assertEquals(1, session.getBids().size());
    }

    @Test
    void placeBidShouldReturnTrueForValidBid() {
        boolean accepted = session.placeBid(
                createBid("bidder-1", item.getId(), 150.0)
        );

        assertTrue(accepted);
    }

    @Test
    void placeBidShouldReturnFalseForInvalidBid() {
        session.placeBid(createBid("bidder-1", item.getId(), 140.0));

        boolean accepted = session.placeBid(
                createBid("bidder-2", item.getId(), 120.0)
        );

        assertFalse(accepted);
    }

    @Test
    void finishAuctionShouldUpdateStatusToFinished() {
        session.finishAuction();

        assertEquals(AuctionStatus.FINISHED, session.getStatus());
        assertNotNull(session.getEndTime());
    }

    @Test
    void startAuctionShouldUpdateStatusToRunning() {
        Item futureItem = ItemFactory.createItem(
                "electronics",
                "ITEM-FUTURE",
                "Future Auction",
                "Future auction item",
                200.0,
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(2),
                "Dell",
                12
        );

        AuctionSession futureSession = new AuctionSession(
                futureItem,
                futureItem.getStartingPrice(),
                futureItem.getEndTime()
        );

        futureSession.startAuction();

        assertEquals(AuctionStatus.RUNNING, futureSession.getStatus());
    }

    @Test
    void getSummaryShouldReturnCorrectAuctionInformation() {
        session.submitBid(createBid("bidder-1", item.getId(), 130.0));
        session.submitBid(createBid("bidder-2", item.getId(), 150.0));

        AuctionSummary summary = session.getSummary();

        assertEquals(item.getId(), summary.itemId());
        assertEquals(150.0, summary.currentPrice());
        assertEquals(2, summary.totalBids());
        assertEquals(AuctionStatus.RUNNING, summary.status());
    }

    @Test
    void constructorShouldLoadExistingBidsCorrectly() {
        List<Bid> existingBids = new ArrayList<>();

        existingBids.add(createBid("bidder-1", item.getId(), 120.0));
        existingBids.add(createBid("bidder-2", item.getId(), 180.0));

        AuctionSession loadedSession = new AuctionSession(
                item,
                item.getStartingPrice(),
                item.getEndTime(),
                existingBids
        );

        assertEquals(180.0, loadedSession.getCurrentHighestBid());
        assertEquals(2, loadedSession.getBids().size());
    }

    @Test
    void submitBidShouldThrowExceptionWhenBidIsNull() {
        assertThrows(NullPointerException.class, () -> session.submitBid(null));
    }

    @Test
    void getBidsShouldReturnImmutableList() {
        session.submitBid(createBid("bidder-1", item.getId(), 125.0));

        List<Bid> bids = session.getBids();

        assertThrows(UnsupportedOperationException.class, () ->
                bids.add(createBid("bidder-2", item.getId(), 140.0))
        );
    }

    private Bid createBid(String bidderId, String itemId, double amount) {
        return new Bid(
                "BID-" + UUID.randomUUID(),
                bidderId,
                itemId,
                amount,
                LocalDateTime.now()
        );
    }
}
