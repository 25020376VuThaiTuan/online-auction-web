package org.example.model;

import java.time.LocalDateTime;

public class Bid extends Entity {
    private final String bidderId;
    private final String itemId;
    private final double amount;
    private final LocalDateTime bidTime;

    public Bid(String id, String bidderId, String itemId, double amount, LocalDateTime bidTime) {
        super(id);
        this.bidderId = bidderId;
        this.itemId = itemId;
        this.amount = amount;
        this.bidTime = bidTime;
    }

    public String getBidderId() {
        return bidderId;
    }

    public String getItemId() {
        return itemId;
    }

    public double getAmount() {
        return amount;
    }

    public LocalDateTime getBidTime() {
        return bidTime;
    }
}
