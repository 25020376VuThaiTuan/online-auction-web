package org.example.auction;

public record AuctionSummary(
        String itemId,
        String itemName,
        AuctionStatus status,
        double currentPrice,
        double minimumNextBid,
        long secondsRemaining,
        int totalBids,
        String highestBidderId
) {
}
