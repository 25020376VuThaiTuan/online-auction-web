package org.example.auction;

import org.example.model.Bid;
import org.example.model.Item;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

public class AuctionSession implements AuctionSubject {
    private final Item item;
    private AuctionStatus status;
    private final List<AuctionObserver> observers = new CopyOnWriteArrayList<>();
    private final List<Bid> bids = new ArrayList<>();
    private double currentHighestBid;
    private LocalDateTime endTime;

    private final ReentrantLock lock = new ReentrantLock();

    public AuctionSession(Item item, double startingPrice, LocalDateTime endTime) {
        this.item = Objects.requireNonNull(item, "item");
        this.currentHighestBid = Math.max(startingPrice, item.getCurrentPrice());
        this.item.setCurrentPrice(this.currentHighestBid);
        this.endTime = endTime == null ? item.getEndTime() : endTime;
        this.item.setEndTime(this.endTime);
        this.status = AuctionRules.resolveStatus(item.getStartTime(), this.endTime, LocalDateTime.now());
    }

    public void startAuction() {
        if (!status.isFinished()) {
            status = AuctionStatus.RUNNING;
        }
    }

    public void finishAuction() {
        status = AuctionStatus.FINISHED;
    }

    public boolean placeBid(Bid bid) {
        return submitBid(bid).accepted();
    }

    public BidValidationResult submitBid(Bid bid) {
        Objects.requireNonNull(bid, "bid");

        lock.lock();
        try {
            AuctionStatus currentStatus = getStatus();
            if (currentStatus != AuctionStatus.RUNNING) {
                return BidValidationResult.rejected(
                        "Auction is not accepting bids.",
                        bid.getAmount(),
                        currentHighestBid,
                        AuctionRules.minimumNextBid(currentHighestBid),
                        currentStatus,
                        endTime
                );
            }

            LocalDateTime bidTime = bid.getBidTime() == null ? LocalDateTime.now() : bid.getBidTime();
            BidValidationResult validation = AuctionRules.validateBid(item, bid.getAmount(), bidTime);
            if (!validation.accepted()) {
                status = validation.status();
                return validation;
            }

            currentHighestBid = bid.getAmount();
            item.setCurrentPrice(currentHighestBid);
            bids.add(bid);
            endTime = validation.effectiveEndTime();
            item.setEndTime(endTime);
            status = AuctionRules.resolveStatus(item.getStartTime(), endTime, LocalDateTime.now());

            notifyObservers();
            return validation;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void notifyObservers() {
        if (bids.isEmpty()) {
            return;
        }

        Bid lastBid = bids.get(bids.size() - 1);
        for (AuctionObserver observer : observers) {
            observer.onNewBid(lastBid);
        }
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public Item getItem() {
        return item;
    }

    public double getCurrentHighestBid() {
        return currentHighestBid;
    }

    public AuctionStatus getStatus() {
        if (status != null && status.isFinished()) {
            return status;
        }

        status = AuctionRules.resolveStatus(item.getStartTime(), endTime, LocalDateTime.now());
        return status;
    }

    public List<Bid> getBids() {
        lock.lock();
        try {
            return List.copyOf(bids);
        } finally {
            lock.unlock();
        }
    }

    public AuctionSummary getSummary() {
        return AuctionRules.buildSummary(item, getBids(), getStatus(), LocalDateTime.now());
    }

    @Override
    public void addObserver(AuctionObserver observer) {
        observers.add(observer);
    }

    @Override
    public void removeObserver(AuctionObserver observer) {
        observers.remove(observer);
    }
}
