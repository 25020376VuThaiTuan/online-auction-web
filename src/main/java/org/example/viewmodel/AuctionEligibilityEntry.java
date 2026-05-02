package org.example.viewmodel;

public class AuctionEligibilityEntry {
    private final String itemId;
    private final String itemName;
    private final String status;
    private final double currentPrice;
    private final double minimumBid;
    private final double requiredDeposit;
    private final double availableBalance;
    private final boolean eligible;

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
        this.itemId = itemId;
        this.itemName = itemName;
        this.status = status;
        this.currentPrice = currentPrice;
        this.minimumBid = minimumBid;
        this.requiredDeposit = requiredDeposit;
        this.availableBalance = availableBalance;
        this.eligible = eligible;
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
        return eligible ? "Yes" : "No";
    }

    public boolean isEligible() {
        return eligible;
    }
}
