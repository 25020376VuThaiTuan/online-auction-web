package org.example.model;
import java.time.LocalDateTime;

public class Vehicle extends Item {
    private String model;
    private int mileage;

    public Vehicle(String id, String itemName, String description, double startingPrice, double currentPrice, LocalDateTime startTime, LocalDateTime endTime, String model, int mileage) {
        super(id, itemName, description, startingPrice, currentPrice, startTime, endTime);
        this.model = model;
        this.mileage = mileage;
    }

    @Override
    public void displayInfo() {
        System.out.println("Phương tiện: " + getItemName() + " | Model: " + model + " | ODO: " + mileage + " km");
    }
}

