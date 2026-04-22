package org.example.model;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public abstract class Item extends Entity {
    private String itemName;
    private String description;
    private double startingPrice;
    private double currentPrice;
    protected LocalDateTime startTime;
    protected LocalDateTime endTime;

    public Item(String id, String itemName, String description, double startingPrice, double currentPrice, LocalDateTime startTime, LocalDateTime endtime) {
        super(id);
        this.itemName = itemName;
        this.description = description;
        this.startingPrice = startingPrice;
        this.currentPrice = startingPrice; // Mới tạo thì giá = giá khởi điểm
        this.startTime = startTime;
        this.endTime = endtime;
    }

    public String getItemName() { return itemName; }
    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice;
    }
    public abstract void displayInfo();

    public String getEndTimeString() {
        if (endTime == null) return "N/A";
        // Định dạng lại thành: Ngày/Tháng/Năm Giờ:Phút
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        return endTime.format(formatter);
    }
}
