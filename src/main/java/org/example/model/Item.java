package org.example.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public abstract class Item extends Entity {
    private String itemName;
    private String description;
    private double startingPrice;
    private double currentPrice;
    private String sellerId;
    private ApprovalStatus approvalStatus = ApprovalStatus.APPROVED;
    protected LocalDateTime startTime;
    protected LocalDateTime endTime;

    public Item(
            String id,
            String itemName,
            String description,
            double startingPrice,
            double currentPrice,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        super(id);
        this.itemName = itemName;
        this.description = description;
        this.startingPrice = startingPrice;
        this.currentPrice = currentPrice;
        this.startTime = startTime;
        this.endTime = endTime;
    }

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

    public void setCurrentPrice(double currentPrice) {
        this.currentPrice = currentPrice;
    }

    public String getSellerId() {
        return sellerId == null ? "" : sellerId;
    }

    public void setSellerId(String sellerId) {
        this.sellerId = sellerId;
    }

    public ApprovalStatus getApprovalStatus() {
        return approvalStatus == null ? ApprovalStatus.APPROVED : approvalStatus;
    }

    public void setApprovalStatus(ApprovalStatus approvalStatus) {
        this.approvalStatus = approvalStatus == null ? ApprovalStatus.APPROVED : approvalStatus;
    }

    public boolean isApproved() {
        return getApprovalStatus() == ApprovalStatus.APPROVED;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public boolean hasStarted(LocalDateTime now) {
        return now == null || startTime == null || !now.isBefore(startTime);
    }

    public boolean isEnded(LocalDateTime now) {
        if (endTime == null) {
            return false;
        }

        LocalDateTime safeNow = now == null ? LocalDateTime.now() : now;
        return !safeNow.isBefore(endTime);
    }

    public long getRemainingSeconds(LocalDateTime now) {
        if (endTime == null) {
            return Long.MAX_VALUE;
        }

        LocalDateTime safeNow = now == null ? LocalDateTime.now() : now;
        return Math.max(0L, Duration.between(safeNow, endTime).getSeconds());
    }

    public abstract void displayInfo();

    public String getEndTimeString() {
        if (endTime == null) {
            return "N/A";
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        return endTime.format(formatter);
    }
}
