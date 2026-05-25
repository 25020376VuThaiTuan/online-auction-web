package org.example.service;

import org.example.auction.AuctionSettlement;
import org.example.auction.AuctionSummary;
import org.example.auction.AuctionStatus;
import org.example.model.Bid;
import org.example.model.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuctionSettlementIntegrationTest {

    private AuctionSettlementService settlementService;

    @BeforeEach
    void setUp() {

        settlementService =
                AuctionSettlementService.getInstance();
    }

    @Test
    void shouldReturnEmptyWhenAuctionNotFinished() {

        Item item = createItem();

        AuctionSummary summary =
                new AuctionSummary(
                        item.getId(),
                        item.getItemName(),
                        AuctionStatus.RUNNING,
                        item.getCurrentPrice(),
                        1100.0,
                        3600,
                        0,
                        null
                );

        Optional<AuctionSettlement> result =
                settlementService.finalizeAuction(
                        item,
                        summary,
                        List.of()
                );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFinalizeAuctionSuccessfully() {

        Item item = createItem();

        Bid winningBid =
                new Bid(
                        UUID.randomUUID().toString(),
                        "winner-id",
                        item.getId(),
                        2500.0,
                        LocalDateTime.now()
                );

        AuctionSummary summary =
                new AuctionSummary(
                        item.getId(),
                        item.getItemName(),
                        AuctionStatus.FINISHED,
                        2500.0,
                        2600.0,
                        0,
                        1,
                        "winner-id"
                );

        Optional<AuctionSettlement> result =
                settlementService.finalizeAuction(
                        item,
                        summary,
                        List.of(winningBid)
                );

        assertTrue(result.isPresent());

        AuctionSettlement settlement =
                result.get();

        assertEquals(
                item.getId(),
                settlement.getItemId()
        );

        assertEquals(
                "winner-id",
                settlement.getWinnerBidderId()
        );

        assertEquals(
                2500.0,
                settlement.getWinningBidAmount()
        );
    }

    @Test
    void shouldReturnEmptyWhenNoWinnerExists() {

        Item item = createItem();

        AuctionSummary summary =
                new AuctionSummary(
                        item.getId(),
                        item.getItemName(),
                        AuctionStatus.FINISHED,
                        item.getCurrentPrice(),
                        1100.0,
                        0,
                        0,
                        null
                );

        Optional<AuctionSettlement> result =
                settlementService.finalizeAuction(
                        item,
                        summary,
                        List.of()
                );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnExistingSettlementIfAlreadyFinalized() {

        Item item = createItem();

        Bid winningBid =
                new Bid(
                        UUID.randomUUID().toString(),
                        "winner-id",
                        item.getId(),
                        3000.0,
                        LocalDateTime.now()
                );

        AuctionSummary summary =
                new AuctionSummary(
                        item.getId(),
                        item.getItemName(),
                        AuctionStatus.FINISHED,
                        3000.0,
                        3100.0,
                        0,
                        1,
                        "winner-id"
                );

        Optional<AuctionSettlement> first =
                settlementService.finalizeAuction(
                        item,
                        summary,
                        List.of(winningBid)
                );

        Optional<AuctionSettlement> second =
                settlementService.finalizeAuction(
                        item,
                        summary,
                        List.of(winningBid)
                );

        assertTrue(first.isPresent());

        assertTrue(second.isPresent());

        assertEquals(
                first.get().getItemId(),
                second.get().getItemId()
        );
    }

    @Test
    void shouldStoreSettlementAfterFinalize() {

        Item item = createItem();

        Bid winningBid =
                new Bid(
                        UUID.randomUUID().toString(),
                        "winner-id",
                        item.getId(),
                        4000.0,
                        LocalDateTime.now()
                );

        AuctionSummary summary =
                new AuctionSummary(
                        item.getId(),
                        item.getItemName(),
                        AuctionStatus.FINISHED,
                        4000.0,
                        4100.0,
                        0,
                        1,
                        "winner-id"
                );

        settlementService.finalizeAuction(
                item,
                summary,
                List.of(winningBid)
        );

        Optional<AuctionSettlement> stored =
                settlementService.getSettlement(
                        item.getId()
                );

        assertTrue(stored.isPresent());

        assertEquals(
                4000.0,
                stored.get().getWinningBidAmount()
        );
    }

    private Item createItem() {

        return new Item(
                UUID.randomUUID().toString(),
                "Gaming Laptop",
                "High-end gaming laptop",
                1000.0,
                1000.0,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(2)
        ) {
            @Override
            public void displayInfo() {
            }
        };
    }
}