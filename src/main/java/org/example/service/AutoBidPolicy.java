package org.example.service;

import org.example.auction.AuctionRules;
import org.example.model.AutoBid;

public final class AutoBidPolicy {
    private static final AutoBidPolicy DEFAULT = new AutoBidPolicy();

    public static AutoBidPolicy defaultPolicy() {
        return DEFAULT;
    }

    public boolean shouldTrigger(AutoBid autoBid, String triggerBidderId, String leadingBidderId) {
        if (autoBid == null || autoBid.getBidderId() == null || autoBid.getBidderId().isBlank()) {
            return false;
        }
        String autoBidderId = autoBid.getBidderId();
        return !autoBidderId.equals(triggerBidderId) && !autoBidderId.equals(leadingBidderId);
    }

    public double nextBidAmount(double currentHighest, AutoBid autoBid) {
        return nextBidAmount(currentHighest, autoBid, Double.POSITIVE_INFINITY);
    }

    public double nextBidAmount(double currentHighest, AutoBid autoBid, double availableBalance) {
        if (autoBid == null) {
            return 0.0;
        }
        double minNext = AuctionRules.minimumNextBid(currentHighest);
        double spendLimit = Math.min(autoBid.getMaxLimit(), availableBalance);
        if (!Double.isFinite(spendLimit) || spendLimit < minNext) {
            return 0.0;
        }
        double requiredIncrement = AuctionRules.minimumIncrement(currentHighest);
        double configuredIncrement = autoBid.getBidIncrement();
        double effectiveIncrement = configuredIncrement > 0.0
                ? Math.max(configuredIncrement, requiredIncrement)
                : requiredIncrement;
        double requestedAmount = roundCurrency(currentHighest + effectiveIncrement);
        return roundCurrency(Math.min(spendLimit, requestedAmount));
    }

    private double roundCurrency(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }
}
