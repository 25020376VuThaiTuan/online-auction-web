package org.example.model;

import java.time.LocalDateTime;

public abstract class Item extends Entity {

    private String itemName;
    private String description;
    private double startingPrice;
    private double currentPrice;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public Item(String id,
                String itemName,
                String description,
                double startingPrice,
                LocalDateTime startTime,
                LocalDateTime endTime) {

        super(id);
        this.itemName = itemName;
        this.description = description;
        this.startingPrice = startingPrice;
        this.currentPrice = startingPrice; // giá ban đầu = giá khởi điểm
        this.startTime = startTime;
        this.endTime = endTime;
    }

    // Getter
    public String getItemName() {
        return itemName;
    }

    public String getDescription() {
        return description;
    }

    public double getStartingPrice() {
        return startingPrice;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    // Setter
    public void setCurrentPrice(double currentPrice) {
        this.currentPrice = currentPrice;
    }

    // Abstract method
    public abstract void displayInfo();
}