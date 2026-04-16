package org.example.auction;

public interface AuctionSubject {
    void addObserver(AuctionObserver observer);
    void removeObserver(AuctionObserver observer);
    void notifyObservers();
}