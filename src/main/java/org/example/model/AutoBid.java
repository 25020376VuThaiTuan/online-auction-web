package org.example.model;

public class AutoBid {
    private int id;
    private String bidderId;
    private String itemId;
    private double maxLimit;

    public AutoBid(int id, String bidderId, String itemId, double maxLimit) {
        this.id = id;
        this.bidderId = bidderId;
        this.itemId = itemId;
        this.maxLimit = maxLimit;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getBidderId() {
        return bidderId;
    }

    public void setBidderId(String bidderId) {
        this.bidderId = bidderId;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public double getMaxLimit() {
        return maxLimit;
    }

    public void setMaxLimit(double maxLimit) {
        this.maxLimit = maxLimit;
    }
}
