package org.example.auction;

import org.example.model.Bid;
import org.example.model.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class AuctionSession implements AuctionSubject {

    private Item item;
    private AuctionStatus status;
    private List<AuctionObserver> observers = new ArrayList<>();
    private List<Bid> bids = new ArrayList<>();

    private ReentrantLock lock = new ReentrantLock();

    public AuctionSession(Item item) {
        this.item = item;
        this.status = AuctionStatus.OPEN;
    }

    public void startAuction() {
        status = AuctionStatus.RUNNING;
    }

    public void finishAuction() {
        status = AuctionStatus.FINISHED;
    }

    public boolean placeBid(Bid bid) {
        lock.lock();
        try {
            if(status != AuctionStatus.RUNNING) return false;

            if(bid.getAmount() > item.getCurrentPrice()) {
                item.setCurrentPrice(bid.getAmount());
                bids.add(bid);
                notifyObservers();
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void addObserver(AuctionObserver observer) {
        observers.add(observer);
    }

    @Override
    public void removeObserver(AuctionObserver observer) {
        observers.remove(observer);
    }

    @Override
    public void notifyObservers() {
        Bid lastBid = bids.get(bids.size() - 1);
        for (AuctionObserver o : observers) {
            o.onNewBid(lastBid);
        }
    }

    public AuctionStatus getStatus() {
        return status;
    }
}