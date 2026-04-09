package org.example.model;
import java.time.LocalDateTime;

public class Art extends Item {
    private String artist;
    private int yearCreated;

    public Art(String id, String itemName, String description, double startingPrice,
               LocalDateTime startTime, LocalDateTime endTime, String artist, int yearCreated) {
        super(id, itemName, description, startingPrice, startTime, endTime);
        this.artist = artist;
        this.yearCreated = yearCreated;
    }

    @Override
    public void displayInfo() {
        System.out.println("Tác phẩm nghệ thuật: " + getItemName() + " | Tác giả: " + artist + " | Năm: " + yearCreated);
    }
}