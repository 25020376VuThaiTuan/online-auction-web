package org.example.service;

import org.example.auction.AuctionRules;
import org.example.auction.AuctionStatus;
import org.example.auction.AuctionSummary;
import org.example.auction.BidValidationResult;
import org.example.model.Item;
import org.example.model.User;

import java.time.LocalDateTime;
import java.util.Optional;

public final class BidAuthorizationPolicy {
    private static final BidAuthorizationPolicy DEFAULT = new BidAuthorizationPolicy();

    public static BidAuthorizationPolicy defaultPolicy() {
        return DEFAULT;
    }

    public Optional<BidValidationResult> findFailure(
            Item item,
            AuctionSummary summary,
            User user,
            double amount,
            boolean hasEntryDeposit,
            double availableBalance
    ) {
        double currentPrice = summary == null ? (item == null ? 0.0 : item.getCurrentPrice()) : summary.currentPrice();
        double minimumNextBid = summary == null ? AuctionRules.minimumNextBid(currentPrice) : summary.minimumNextBid();
        AuctionStatus status = summary == null ? AuctionStatus.OPEN : summary.status();
        LocalDateTime effectiveEndTime = item == null ? null : item.getEndTime();

        if (user == null) {
            return Optional.of(rejected(
                    "Authentication required to place bids.",
                    amount,
                    currentPrice,
                    minimumNextBid,
                    status,
                    effectiveEndTime
            ));
        }

        if (item != null && item.getSellerId() != null && item.getSellerId().equalsIgnoreCase(user.getId())) {
            return Optional.of(rejected(
                    "Item creators cannot bid on their own auctions.",
                    amount,
                    currentPrice,
                    minimumNextBid,
                    status,
                    effectiveEndTime
            ));
        }

        if (!hasEntryDeposit) {
            return Optional.of(rejected(
                    "Confirm auction entry and lock the deposit before placing a bid.",
                    amount,
                    currentPrice,
                    minimumNextBid,
                    status,
                    effectiveEndTime
            ));
        }

        if (Double.isFinite(amount) && amount > 0.0 && amount > roundCurrency(availableBalance)) {
            return Optional.of(rejected(
                    "Available balance is lower than the bid amount after locked deposits.",
                    amount,
                    currentPrice,
                    minimumNextBid,
                    status,
                    effectiveEndTime
            ));
        }

        return Optional.empty();
    }

    BidValidationResult failureOrNull(
            Item item,
            AuctionSummary summary,
            User user,
            double amount,
            boolean hasEntryDeposit,
            double availableBalance
    ) {
        return findFailure(item, summary, user, amount, hasEntryDeposit, availableBalance).orElse(null);
    }

    private BidValidationResult rejected(
            String message,
            double amount,
            double currentPrice,
            double minimumNextBid,
            AuctionStatus status,
            LocalDateTime effectiveEndTime
    ) {
        return BidValidationResult.rejected(
                message,
                amount,
                currentPrice,
                minimumNextBid,
                status,
                effectiveEndTime
        );
    }

    private double roundCurrency(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }
}
