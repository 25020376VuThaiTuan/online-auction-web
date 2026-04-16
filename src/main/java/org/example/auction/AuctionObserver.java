package org.example.auction;

import org.example.model.Bid;

public interface AuctionObserver {
    void onNewBid(Bid bid);
}