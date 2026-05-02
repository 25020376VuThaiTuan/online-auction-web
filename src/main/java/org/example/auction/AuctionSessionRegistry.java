package org.example.auction;

import org.example.model.Item;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AuctionSessionRegistry {
    private static final AuctionSessionRegistry INSTANCE = new AuctionSessionRegistry();

    private final Map<String, AuctionSession> sessions = new ConcurrentHashMap<>();

    private AuctionSessionRegistry() {
    }

    public static AuctionSessionRegistry getInstance() {
        return INSTANCE;
    }

    public void clear() {
        sessions.clear();
    }

    public void preloadSessions(List<Item> items) {
        preloadSessions(items, Map.of());
    }

    public void preloadSessions(List<Item> items, Map<String, List<org.example.model.Bid>> bidHistoryByItemId) {
        if (items == null) {
            return;
        }

        for (Item item : items) {
            List<org.example.model.Bid> existingBids = bidHistoryByItemId == null
                    ? List.of()
                    : bidHistoryByItemId.getOrDefault(item.getId(), List.of());
            getOrCreateSession(item, existingBids);
        }
    }

    public AuctionSession getOrCreateSession(Item item) {
        return getOrCreateSession(item, List.of());
    }

    public AuctionSession getOrCreateSession(Item item, List<org.example.model.Bid> existingBids) {
        return sessions.compute(item.getId(), (itemId, existingSession) -> {
            if (existingSession == null || existingSession.getItem() != item) {
                return new AuctionSession(item, item.getCurrentPrice(), item.getEndTime(), existingBids);
            }
            return existingSession;
        });
    }

    public AuctionSession findByItemId(String itemId) {
        return sessions.get(itemId);
    }
}
