package org.example.auction;

import org.example.model.Bid;
import org.example.model.Item;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

public class AuctionSession implements AuctionSubject {
    private final Item item;
    private volatile AuctionStatus status;
    private final List<AuctionObserver> observers = new CopyOnWriteArrayList<>();
    private final List<Bid> bids = new ArrayList<>();
    private volatile double currentHighestBid;
    private volatile LocalDateTime endTime;

    private final ReentrantLock lock = new ReentrantLock();

    public AuctionSession(Item item, double startingPrice, LocalDateTime endTime) {
        this(item, startingPrice, endTime, List.of());
    }

    public AuctionSession(Item item, double startingPrice, LocalDateTime endTime, List<Bid> existingBids) {
        this.item = Objects.requireNonNull(item, "item");
        this.currentHighestBid = Math.max(startingPrice, item.getCurrentPrice());
        this.item.setCurrentPrice(this.currentHighestBid);
        this.endTime = endTime == null ? item.getEndTime() : endTime;
        this.item.setEndTime(this.endTime);

        if (existingBids != null && !existingBids.isEmpty()) {
            this.bids.addAll(
                    existingBids.stream()
                            .sorted(Comparator.comparing(bid -> bid.getBidTime() == null ? LocalDateTime.MIN : bid.getBidTime()))
                            .toList()
            );
            this.currentHighestBid = Math.max(this.currentHighestBid, this.bids.get(this.bids.size() - 1).getAmount());
            this.item.setCurrentPrice(this.currentHighestBid);
        }

        this.status = AuctionRules.resolveStatus(item.getStartTime(), this.endTime, LocalDateTime.now());
    }

    public void startAuction() {
        lock.lock();
        try {
            if (!status.isFinished()) {
                status = AuctionStatus.RUNNING;
            }
        } finally {
            lock.unlock();
        }
    }

    public void finishAuction() {
        lock.lock();
        try {
            status = AuctionStatus.FINISHED;
        } finally {
            lock.unlock();
        }
    }

    public boolean placeBid(Bid bid) {
        return submitBid(bid).accepted();
    }

    public BidValidationResult submitBid(Bid bid) {
        Objects.requireNonNull(bid, "bid");

        // One lock per auction session prevents concurrent bidders from causing lost updates.
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
            // Anti-sniping logic can extend the end time when a valid late bid arrives.
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
        Bid lastBid;
        lock.lock();
        try {
            if (bids.isEmpty()) {
                return;
            }
            lastBid = bids.get(bids.size() - 1);
        } finally {
            lock.unlock();
        }
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
        lock.lock();
        try {
            if (status != null && status.isFinished()) {
                return status;
            }

            status = AuctionRules.resolveStatus(item.getStartTime(), endTime, LocalDateTime.now());
            return status;
        } finally {
            lock.unlock();
        }
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
