package org.example.viewmodel;

import org.example.util.AuctionDisplayFormatter;

public class AuctionListEntry {
    private final String itemId;
    private final String itemName;
    private final String status;
    private final double currentPrice;
    private final double minimumNextBid;
    private final String endTimeString;
    private final long remainingSeconds;

    public AuctionListEntry(
            String itemId,
            String itemName,
            String status,
            double currentPrice,
            double minimumNextBid,
            String endTimeString,
            long remainingSeconds
    ) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.status = status;
        this.currentPrice = currentPrice;
        this.minimumNextBid = minimumNextBid;
        this.endTimeString = endTimeString;
        this.remainingSeconds = remainingSeconds;
    }

    public String getItemId() {
        return itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public String getStatus() {
        return status;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public double getMinimumNextBid() {
        return minimumNextBid;
    }

    public String getEndTimeString() {
        return endTimeString;
    }

    public String getRemainingTime() {
        return AuctionDisplayFormatter.formatRemainingTime(remainingSeconds);
    }
}
