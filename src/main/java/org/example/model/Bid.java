package org.example.model;

import java.time.LocalDateTime;

public class Bid {
    private String bidderId;
    private double amount;
    private LocalDateTime time;

    public Bid(String bidderId, double amount) {
        this.bidderId = bidderId;
        this.amount = amount;
        this.time = LocalDateTime.now();
    }

    public double getAmount() {
        return amount;
    }

    public String getBidderId() {
        return bidderId;
    }

    public LocalDateTime getTime() {
        return time;
    }
}