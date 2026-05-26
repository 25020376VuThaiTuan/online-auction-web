package org.example.model;

import java.io.Serial;
import java.io.Serializable;

public class AutoBid implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private int id;
    private String bidderId;
    private String itemId;
    private double maxLimit;
    private double bidIncrement;

    public AutoBid(int id, String bidderId, String itemId, double maxLimit) {
        this(id, bidderId, itemId, maxLimit, 0.0);
    }

    public AutoBid(int id, String bidderId, String itemId, double maxLimit, double bidIncrement) {
        this.id = id;
        this.bidderId = bidderId;
        this.itemId = itemId;
        this.maxLimit = maxLimit;
        this.bidIncrement = bidIncrement;
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

    public double getBidIncrement() {
        return bidIncrement;
    }

    public void setBidIncrement(double bidIncrement) {
        this.bidIncrement = bidIncrement;
    }
}
