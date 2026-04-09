package org.example.model;
import java.time.LocalDateTime;

public class Item extends Entity {
    private String itemName;
    private String description;
    private double startingPrice;
    private double currentPrice;
    private LocalDateTime startTime;
    private LocalDateTime endtime;

    public Item(String id, String itemName, String description, double startingPrice, double currentPrice, LocalDateTime startTime, LocalDateTime endtime) {
        super(id);
        this.itemName = itemName;
        this.description = description;
        this.startingPrice = startingPrice;
        this.currentPrice = startingPrice; // Mới tạo thì giá = giá khởi điểm
        this.startTime = startTime;
        this.endtime = endtime;
    }

    public String getItemName() { return itemName; }
    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice;
    }
    public abstract void displayInfo();
}
