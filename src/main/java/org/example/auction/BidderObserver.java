package org.example.auction;

import org.example.model.AuctionSession;

public class BidderObserver implements AuctionObserver {

    private String name;

    public BidderObserver(String name) {
        this.name = name;
    }

    @Override
    public void update(AuctionSession auction) {
        System.out.println(name + 
            " notified: New price = " 
            + auction.getCurrentPrice());
    }
}