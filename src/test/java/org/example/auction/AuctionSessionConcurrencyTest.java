package org.example.auction;

import org.example.model.Bid;
import org.example.model.Item;
import org.example.model.ItemFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionSessionConcurrencyTest {
    @Test
    void concurrentSameAmountBidsCannotBothWin() throws Exception {
        Item item = ItemFactory.createItem(
                "electronics",
                "CONC-" + UUID.randomUUID().toString().substring(0, 8),
                "Concurrent Bid Test",
                "Test item",
                100.0,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(5),
                "Brand",
                12
        );
        AuctionSession session = new AuctionSession(item, item.getStartingPrice(), item.getEndTime());
        assertTrue(session.submitBid(bid("bidder-a", item.getId(), 110.0)).accepted());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<BidValidationResult> first = executor.submit(() -> submitAfterStart(session, ready, start, "bidder-b", item.getId()));
            Future<BidValidationResult> second = executor.submit(() -> submitAfterStart(session, ready, start, "bidder-c", item.getId()));

            ready.await();
            start.countDown();

            List<BidValidationResult> results = List.of(first.get(), second.get());
            long acceptedCount = results.stream().filter(BidValidationResult::accepted).count();

            assertEquals(1L, acceptedCount);
            assertEquals(120.0, session.getSummary().currentPrice(), 0.001);
            assertEquals(2, session.getBids().size());
        } finally {
            executor.shutdownNow();
        }
    }

    private BidValidationResult submitAfterStart(
            AuctionSession session,
            CountDownLatch ready,
            CountDownLatch start,
            String bidderId,
            String itemId
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return session.submitBid(bid(bidderId, itemId, 120.0));
    }

    private Bid bid(String bidderId, String itemId, double amount) {
        return new Bid("BID-" + UUID.randomUUID(), bidderId, itemId, amount, LocalDateTime.now());
    }
}
