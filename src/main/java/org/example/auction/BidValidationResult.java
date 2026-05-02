package org.example.auction;

import java.time.LocalDateTime;

public record BidValidationResult(
        boolean accepted,
        String message,
        double attemptedAmount,
        double currentPrice,
        double minimumAllowedBid,
        AuctionStatus status,
        LocalDateTime effectiveEndTime
) {
    public static BidValidationResult accepted(
            String message,
            double attemptedAmount,
            double currentPrice,
            double minimumAllowedBid,
            AuctionStatus status,
            LocalDateTime effectiveEndTime
    ) {
        return new BidValidationResult(true, message, attemptedAmount, currentPrice, minimumAllowedBid, status, effectiveEndTime);
    }

    public static BidValidationResult rejected(
            String message,
            double attemptedAmount,
            double currentPrice,
            double minimumAllowedBid,
            AuctionStatus status,
            LocalDateTime effectiveEndTime
    ) {
        return new BidValidationResult(false, message, attemptedAmount, currentPrice, minimumAllowedBid, status, effectiveEndTime);
    }
}
