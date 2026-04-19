package org.example.auction;

public enum AuctionStatus {
    OPEN,
    RUNNING,
    FINISHED,
    PAID,
    CANCELLED;

    public boolean isFinished() {
        return this == FINISHED || this == PAID || this == CANCELLED;
    }
}