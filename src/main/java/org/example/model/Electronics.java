package org.example.model;
import java.time.LocalDateTime;

public class Electronics extends Item {
    private String brand;
    private int warrantyMonths;

    public Electronics(String id, String itemName, String description, double startingPrice,
                       LocalDateTime startTime, LocalDateTime endTime, String brand, int warrantyMonths) {
        super(id, itemName, description, startingPrice, startTime, endTime); // Gọi constructor của lớp Item
        this.brand = brand;
        this.warrantyMonths = warrantyMonths;
    }

    @Override
    public void displayInfo() {
        System.out.println("Đồ điện tử: " + getItemName() + " | Thương hiệu: " + brand + " | Bảo hành: " + warrantyMonths + " tháng");
    }
}
