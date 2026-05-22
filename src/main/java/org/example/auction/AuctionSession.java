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

    private final ReentrantLock lock = new ReentrantLock(true);

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
                LocalDateTime now = LocalDateTime.now();
                item.setStartTime(now);
                if (endTime != null && !now.isBefore(endTime)) {
                    endTime = now.plusHours(1);
                    item.setEndTime(endTime);
                }
                status = AuctionStatus.RUNNING;
            }
        } finally {
            lock.unlock();
        }
    }

    public void finishAuction() {
        lock.lock();
        try {
            endTime = LocalDateTime.now();
            item.setEndTime(endTime);
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

        Bid acceptedBid;
        BidValidationResult acceptedResult;
        // One lock per auction session prevents concurrent bidders from causing lost updates.
        lock.lock();
        try {
            AuctionStatus currentStatus = resolveStatusLocked(LocalDateTime.now());
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

            acceptedBid = bid;
            acceptedResult = validation;
        } finally {
            lock.unlock();
        }

        notifyObservers(acceptedBid);
        return acceptedResult;
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
        notifyObservers(lastBid);
    }

    private void notifyObservers(Bid lastBid) {
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
            return resolveStatusLocked(LocalDateTime.now());
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
        lock.lock();
        try {
            LocalDateTime now = LocalDateTime.now();
            return AuctionRules.buildSummary(item, List.copyOf(bids), resolveStatusLocked(now), now);
        } finally {
            lock.unlock();
        }
    }

    private AuctionStatus resolveStatusLocked(LocalDateTime now) {
        if (status != null && status.isFinished()) {
            return status;
        }

        status = AuctionRules.resolveStatus(item.getStartTime(), endTime, now);
        return status;
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
