package org.example.model;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuctionStore implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private List<Item> items;
    private Map<String, List<Bid>> bidHistoryByItemId;
    private Map<String, List<AutoBid>> autoBidsByItemId;

    public AuctionStore(List<Item> items, Map<String, List<Bid>> bidHistoryByItemId) {
        this(items, bidHistoryByItemId, Map.of());
    }

    public AuctionStore(
            List<Item> items,
            Map<String, List<Bid>> bidHistoryByItemId,
            Map<String, List<AutoBid>> autoBidsByItemId
    ) {
        this.items = copyList(items);
        this.bidHistoryByItemId = copyNestedListMap(bidHistoryByItemId);
        this.autoBidsByItemId = copyNestedListMap(autoBidsByItemId);
    }

    public static AuctionStore empty() {
        return new AuctionStore(List.of(), Map.of(), Map.of());
    }

    public List<Item> getItems() {
        return copyList(items);
    }

    public Map<String, List<Bid>> getBidHistoryByItemId() {
        return copyNestedListMap(bidHistoryByItemId);
    }

    public Map<String, List<AutoBid>> getAutoBidsByItemId() {
        return copyNestedListMap(autoBidsByItemId);
    }

    @Serial
    private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
        inputStream.defaultReadObject();
        items = copyList(items);
        bidHistoryByItemId = copyNestedListMap(bidHistoryByItemId);
        autoBidsByItemId = copyNestedListMap(autoBidsByItemId);
    }

    private static <T> List<T> copyList(List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private static <T> Map<String, List<T>> copyNestedListMap(Map<String, List<T>> source) {
        Map<String, List<T>> copy = new HashMap<>();
        if (source == null) {
            return copy;
        }
        for (Map.Entry<String, List<T>> entry : source.entrySet()) {
            copy.put(entry.getKey(), copyList(entry.getValue()));
        }
        return copy;
    }
}
