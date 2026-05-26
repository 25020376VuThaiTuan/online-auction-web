package org.example.service;

import org.example.auction.AuctionSession;
import org.example.auction.AuctionStatus;
import org.example.auction.BidValidationResult;
import org.example.model.Bid;
import org.example.model.Item;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class AuctionWorkflowStressTest {

    @Test
    void shouldHandleConcurrentBidsSafely() throws Exception {

        Item item = createItem();

        AuctionSession session =
                new AuctionSession(
                        item,
                        item.getStartingPrice(),
                        item.getEndTime()
                );

        int totalThreads = 50;

        ExecutorService executor =
                Executors.newFixedThreadPool(10);

        CountDownLatch latch =
                new CountDownLatch(totalThreads);

        for (int i = 0; i < totalThreads; i++) {

            final int index = i;

            executor.submit(() -> {

                try {

                    double amount =
                            1050.0 + (index * 100);

                    Bid bid =
                            new Bid(
                                    UUID.randomUUID().toString(),
                                    "bidder-" + index,
                                    item.getId(),
                                    amount,
                                    LocalDateTime.now()
                            );

                    BidValidationResult result =
                            session.submitBid(bid);

                    assertNotNull(result);

                } finally {

                    latch.countDown();
                }
            });
        }

        latch.await();

        executor.shutdown();

        assertTrue(
                session.getCurrentHighestBid() >= 1050.0
        );

        assertFalse(
                session.getBids().isEmpty()
        );
    }

    @Test
    void shouldKeepAuctionConsistentAfterHeavyLoad()
            throws Exception {

        Item item = createItem();

        AuctionSession session =
                new AuctionSession(
                        item,
                        item.getStartingPrice(),
                        item.getEndTime()
                );

        ExecutorService executor =
                Executors.newFixedThreadPool(20);

        int totalBids = 200;

        CountDownLatch latch =
                new CountDownLatch(totalBids);

        for (int i = 0; i < totalBids; i++) {

            final int value = i;

            executor.submit(() -> {

                try {

                    double amount =
                            1050.0 + value;

                    Bid bid =
                            new Bid(
                                    UUID.randomUUID().toString(),
                                    "user-" + value,
                                    item.getId(),
                                    amount,
                                    LocalDateTime.now()
                            );

                    BidValidationResult result =
                            session.submitBid(bid);

                    assertNotNull(result);

                } finally {

                    latch.countDown();
                }
            });
        }

        latch.await();

        executor.shutdown();

        assertNotNull(
                session.getSummary()
        );

        assertEquals(
                session.getCurrentHighestBid(),
                item.getCurrentPrice()
        );
    }

    @Test
    void shouldAllowAuctionToFinishAfterStressLoad() {

        Item item = createItem();

        AuctionSession session =
                new AuctionSession(
                        item,
                        item.getStartingPrice(),
                        item.getEndTime()
                );

        for (int i = 0; i < 100; i++) {

            Bid bid =
                    new Bid(
                            UUID.randomUUID().toString(),
                            "offline-user-" + i,
                            item.getId(),
                            1100.0 + (i * 50),
                            LocalDateTime.now()
                    );

            session.submitBid(bid);
        }

        session.finishAuction();

        assertEquals(
                AuctionStatus.FINISHED,
                session.getStatus()
        );
    }

    private Item createItem() {

        return new Item(
                UUID.randomUUID().toString(),
                "Gaming Laptop",
                "Stress test item",
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