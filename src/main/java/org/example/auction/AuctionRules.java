package org.example.auction;

import org.example.model.Bid;
import org.example.model.Item;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

public final class AuctionRules {
    public static final long DEFAULT_EXTENSION_TRIGGER_SECONDS = 15;
    public static final long DEFAULT_EXTENSION_SECONDS = 60;

    private AuctionRules() {
    }

    public static AuctionStatus resolveStatus(LocalDateTime startTime, LocalDateTime endTime, LocalDateTime now) {
        LocalDateTime safeNow = now == null ? LocalDateTime.now() : now;

        if (endTime != null && !safeNow.isBefore(endTime)) {
            return AuctionStatus.FINISHED;
        }

        if (startTime != null && safeNow.isBefore(startTime)) {
            return AuctionStatus.OPEN;
        }

        return AuctionStatus.RUNNING;
    }

    public static double minimumIncrement(double currentPrice) {
        double safePrice = Math.max(0.0, currentPrice);

        if (safePrice < 1000.0) {
            return 10.0;
        }
        if (safePrice < 5000.0) {
            return 50.0;
        }
        if (safePrice < 1_0000.0) {
            return 100.0;
        }
        if (safePrice < 5_0000.0) {
            return 250.0;
        }
        return 500.0;
    }

    public static double minimumNextBid(double currentPrice) {
        return roundCurrency(currentPrice + minimumIncrement(currentPrice));
    }

    public static double requiredDeposit(double currentPrice) {
        return roundCurrency(Math.max(25.0, minimumNextBid(currentPrice) * 0.2));
    }

    public static BidValidationResult validateBid(Item item, double bidAmount, LocalDateTime now) {
        Objects.requireNonNull(item, "item");

        LocalDateTime safeNow = now == null ? LocalDateTime.now() : now;
        AuctionStatus status = resolveStatus(item.getStartTime(), item.getEndTime(), safeNow);
        double currentPrice = item.getCurrentPrice();
        double minimumAllowedBid = minimumNextBid(currentPrice);

        // Invalid numbers are treated as business validation failures, not system exceptions.
        if (!Double.isFinite(bidAmount) || bidAmount <= 0) {
            return BidValidationResult.rejected(
                    "Bid amount must be a positive number.",
                    bidAmount,
                    currentPrice,
                    minimumAllowedBid,
                    status,
                    item.getEndTime()
            );
        }

        // OPEN means the auction exists but has not reached its start time yet.
        if (status == AuctionStatus.OPEN) {
            return BidValidationResult.rejected(
                    "Auction has not started yet.",
                    bidAmount,
                    currentPrice,
                    minimumAllowedBid,
                    status,
                    item.getEndTime()
            );
        }

        // FINISHED/PAID/CANCELLED are all terminal states that must reject new bids.
        if (status != AuctionStatus.RUNNING) {
            return BidValidationResult.rejected(
                    "Auction is no longer accepting bids.",
                    bidAmount,
                    currentPrice,
                    minimumAllowedBid,
                    status,
                    item.getEndTime()
            );
        }

        // This is the core price validation required by the assignment spec.
        if (bidAmount < minimumAllowedBid) {
            return BidValidationResult.rejected(
                    "Bid is too low. Minimum allowed bid is " + minimumAllowedBid + ".",
                    bidAmount,
                    currentPrice,
                    minimumAllowedBid,
                    status,
                    item.getEndTime()
            );
        }

        LocalDateTime extendedEndTime = calculateExtendedEndTime(
                item.getEndTime(),
                safeNow,
                DEFAULT_EXTENSION_TRIGGER_SECONDS,
                DEFAULT_EXTENSION_SECONDS
        );

        String message = extendedEndTime != null && !extendedEndTime.equals(item.getEndTime())
                ? "Bid accepted. Auction end time was extended."
                : "Bid accepted.";

        return BidValidationResult.accepted(
                message,
                bidAmount,
                currentPrice,
                minimumAllowedBid,
                status,
                extendedEndTime
        );
    }

    public static LocalDateTime calculateExtendedEndTime(
            LocalDateTime endTime,
            LocalDateTime bidTime,
            long extensionTriggerSeconds,
            long extensionSeconds
    ) {
        if (endTime == null || bidTime == null || extensionSeconds <= 0) {
            return endTime;
        }

        // If a valid bid lands in the last X seconds, extend the auction by Y seconds.
        long secondsRemaining = ChronoUnit.SECONDS.between(bidTime, endTime);
        if (secondsRemaining >= 0 && secondsRemaining <= extensionTriggerSeconds) {
            return endTime.plusSeconds(extensionSeconds);
        }

        return endTime;
    }

    public static long remainingSeconds(LocalDateTime endTime, LocalDateTime now) {
        if (endTime == null) {
            return Long.MAX_VALUE;
        }

        LocalDateTime safeNow = now == null ? LocalDateTime.now() : now;
        return Math.max(0L, ChronoUnit.SECONDS.between(safeNow, endTime));
    }

    public static AuctionSummary buildSummary(Item item, List<Bid> bids, AuctionStatus status, LocalDateTime now) {
        Objects.requireNonNull(item, "item");

        List<Bid> safeBids = bids == null ? List.of() : bids;
        String highestBidderId = safeBids.isEmpty() ? null : safeBids.getLast().getBidderId();

        return new AuctionSummary(
                item.getId(),
                item.getItemName(),
                status,
                item.getCurrentPrice(),
                minimumNextBid(item.getCurrentPrice()),
                remainingSeconds(item.getEndTime(), now),
                safeBids.size(),
                highestBidderId
        );
    }

    private static double roundCurrency(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }
}
