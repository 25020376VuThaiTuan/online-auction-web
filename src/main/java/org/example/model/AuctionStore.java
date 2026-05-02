package org.example.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuctionStore implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<Item> items;
    private final Map<String, List<Bid>> bidHistoryByItemId;

    public AuctionStore(List<Item> items, Map<String, List<Bid>> bidHistoryByItemId) {
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
        this.bidHistoryByItemId = new HashMap<>();

        if (bidHistoryByItemId != null) {
            for (Map.Entry<String, List<Bid>> entry : bidHistoryByItemId.entrySet()) {
                this.bidHistoryByItemId.put(
                        entry.getKey(),
                        entry.getValue() == null ? new ArrayList<>() : new ArrayList<>(entry.getValue())
                );
            }
        }
    }

    public static AuctionStore empty() {
        return new AuctionStore(List.of(), Map.of());
    }

    public List<Item> getItems() {
        return new ArrayList<>(items);
    }

    public Map<String, List<Bid>> getBidHistoryByItemId() {
        Map<String, List<Bid>> copy = new HashMap<>();
        for (Map.Entry<String, List<Bid>> entry : bidHistoryByItemId.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }
}
