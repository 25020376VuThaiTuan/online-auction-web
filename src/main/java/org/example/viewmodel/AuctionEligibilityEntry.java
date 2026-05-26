package org.example.viewmodel;

import org.example.util.AuctionDisplayFormatter;

public class AuctionEligibilityEntry {
    private final String itemId;
    private final String itemName;
    private final String status;
    private final double currentPrice;
    private final double minimumBid;
    private final double requiredDeposit;
    private final double availableBalance;
    private final boolean eligible;
    private final boolean depositConfirmed;
    private final String endTimeString;
    private final long remainingSeconds;

    public AuctionEligibilityEntry(
            String itemId,
            String itemName,
            String status,
            double currentPrice,
            double minimumBid,
            double requiredDeposit,
            double availableBalance,
            boolean eligible
    ) {
        this(itemId, itemName, status, currentPrice, minimumBid, requiredDeposit, availableBalance, eligible, false);
    }

    public AuctionEligibilityEntry(
            String itemId,
            String itemName,
            String status,
            double currentPrice,
            double minimumBid,
            double requiredDeposit,
            double availableBalance,
            boolean eligible,
            boolean depositConfirmed
    ) {
        this(
                itemId,
                itemName,
                status,
                currentPrice,
                minimumBid,
                requiredDeposit,
                availableBalance,
                eligible,
                depositConfirmed,
                "N/A",
                0L
        );
    }

    public AuctionEligibilityEntry(
            String itemId,
            String itemName,
            String status,
            double currentPrice,
            double minimumBid,
            double requiredDeposit,
            double availableBalance,
            boolean eligible,
            boolean depositConfirmed,
            String endTimeString,
            long remainingSeconds
    ) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.status = status;
        this.currentPrice = currentPrice;
        this.minimumBid = minimumBid;
        this.requiredDeposit = requiredDeposit;
        this.availableBalance = availableBalance;
        this.eligible = eligible;
        this.depositConfirmed = depositConfirmed;
        this.endTimeString = endTimeString == null || endTimeString.isBlank() ? "N/A" : endTimeString;
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

    public double getMinimumBid() {
        return minimumBid;
    }

    public double getRequiredDeposit() {
        return requiredDeposit;
    }

    public double getAvailableBalance() {
        return availableBalance;
    }

    public String getEligibleText() {
        if (depositConfirmed) {
            return "Entered";
        }
        return eligible ? "Can Enter" : "No";
    }

    public boolean isEligible() {
        return eligible;
    }

    public boolean isDepositConfirmed() {
        return depositConfirmed;
    }

    public String getEndTimeString() {
        return endTimeString;
    }

    public long getRemainingSeconds() {
        return remainingSeconds;
    }

    public String getRemainingTime() {
        return AuctionDisplayFormatter.formatRemainingTime(remainingSeconds);
    }
}
