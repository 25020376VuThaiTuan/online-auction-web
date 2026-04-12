package org.example.model;
import java.time.LocalDateTime;

public class Bid extends Entity {
    private String bidderId;
    private String itemId;
    private double amount;
    private LocalDateTime bidTime;

    public Bid(String id, String bidderId, String itemId, double amount, LocalDateTime bidTime) {
        super(id);
        this.bidderId = bidderId;
        this.itemId = itemId;
        this.amount = amount;
        this.bidTime = bidTime;
    }

    public double getAmount() { return amount; }
}