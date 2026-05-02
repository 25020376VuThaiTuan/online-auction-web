package org.example.model;

public record StoreSnapshot(AuctionStore store, long version) {
    public StoreSnapshot {
        store = store == null ? AuctionStore.empty() : store;
        version = Math.max(0L, version);
    }
}
