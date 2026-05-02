package org.example.service;

import org.example.auction.AuctionRules;
import org.example.auction.AuctionSeedData;
import org.example.auction.AuctionSession;
import org.example.auction.AuctionSessionRegistry;
import org.example.auction.AuctionSummary;
import org.example.auction.BidValidationResult;
import org.example.model.AuctionStore;
import org.example.model.ApprovalStatus;
import org.example.model.Bid;
import org.example.model.DataManager;
import org.example.model.Item;
import org.example.model.ItemFactory;
import org.example.model.StoreSnapshot;
import org.example.model.User;
import org.example.viewmodel.AuctionListEntry;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class AuctionWorkflowService {
    private static final int MAX_STORE_SAVE_RETRIES = 3;
    private static final AuctionWorkflowService INSTANCE = new AuctionWorkflowService();

    private final DataManager dataManager = DataManager.getInstance();
    private final AuctionSessionRegistry sessionRegistry = AuctionSessionRegistry.getInstance();

    private boolean initialized;
    private long loadedStoreVersion;
    private List<Item> items = new ArrayList<>();
    private Map<String, List<Bid>> bidHistoryByItemId = new HashMap<>();

    private AuctionWorkflowService() {
    }

    public static AuctionWorkflowService getInstance() {
        return INSTANCE;
    }

    public synchronized List<AuctionListEntry> getAuctionListEntries() {
        ensureInitialized();
        refreshFromStoreIfChanged();

        List<AuctionListEntry> entries = new ArrayList<>();
        for (Item item : items) {
            if (!item.isApproved()) {
                continue;
            }
            AuctionSummary summary = getSummary(item.getId());
            entries.add(new AuctionListEntry(
                    item.getId(),
                    item.getItemName(),
                    summary.status().name(),
                    summary.currentPrice(),
                    summary.minimumNextBid(),
                    item.getEndTimeString(),
                    summary.secondsRemaining()
            ));
        }
        return entries;
    }

    public synchronized List<Item> getAllItems() {
        ensureInitialized();
        refreshFromStoreIfChanged();
        return new ArrayList<>(items);
    }

    public synchronized List<Item> getPendingApprovalItems() {
        ensureInitialized();
        refreshFromStoreIfChanged();
        return items.stream()
                .filter(item -> item.getApprovalStatus() == ApprovalStatus.PENDING)
                .toList();
    }

    public synchronized List<Item> getItemsForSeller(String sellerId) {
        ensureInitialized();
        refreshFromStoreIfChanged();
        return items.stream()
                .filter(item -> item.getSellerId().equalsIgnoreCase(sellerId == null ? "" : sellerId))
                .toList();
    }

    public synchronized Optional<Item> findItemById(String itemId) {
        ensureInitialized();
        refreshFromStoreIfChanged();
        return findItemInternal(itemId);
    }

    public synchronized AuctionSummary getSummary(String itemId) {
        ensureInitialized();
        refreshFromStoreIfChanged();
        return getSessionForItem(itemId).getSummary();
    }

    public synchronized List<Bid> getBidHistory(String itemId) {
        ensureInitialized();
        refreshFromStoreIfChanged();
        return getSessionForItem(itemId).getBids();
    }

    public synchronized BidValidationResult placeBid(String itemId, User user, double amount) {
        ensureInitialized();
        Objects.requireNonNull(user, "user");

        for (int attempt = 0; attempt < MAX_STORE_SAVE_RETRIES; attempt++) {
            StoreSnapshot snapshot = normalizeSnapshot(dataManager.loadSnapshot());
            AuctionStore workingStore = snapshot.store();
            List<Item> workingItems = new ArrayList<>(workingStore.getItems());
            Map<String, List<Bid>> workingBidHistory = copyBidHistory(workingStore.getBidHistoryByItemId());

            Item item = findItemById(workingItems, itemId)
                    .orElseThrow(() -> new IllegalArgumentException("Auction item not found: " + itemId));

            AuctionSession session = new AuctionSession(
                    item,
                    item.getCurrentPrice(),
                    item.getEndTime(),
                    workingBidHistory.getOrDefault(itemId, List.of())
            );

            Bid bid = new Bid(
                    "BID-" + UUID.randomUUID(),
                    user.getUsername(),
                    itemId,
                    amount,
                    LocalDateTime.now()
            );

            BidValidationResult result = session.submitBid(bid);
            if (!result.accepted()) {
                applySnapshot(snapshot);
                return result;
            }

            workingBidHistory.put(itemId, new ArrayList<>(session.getBids()));
            AuctionStore updatedStore = new AuctionStore(workingItems, workingBidHistory);
            Optional<StoreSnapshot> savedSnapshot = dataManager.saveStoreIfVersionMatches(updatedStore, snapshot.version());
            if (savedSnapshot.isPresent()) {
                applySnapshot(savedSnapshot.get());
                return result;
            }
        }

        refreshFromStoreIfChanged();
        AuctionSession latestSession = getSessionForItem(itemId);
        return BidValidationResult.rejected(
                "Auction changed while your bid was being saved. Review the latest price and try again.",
                amount,
                latestSession.getCurrentHighestBid(),
                AuctionRules.minimumNextBid(latestSession.getCurrentHighestBid()),
                latestSession.getStatus(),
                latestSession.getEndTime()
        );
    }

    public synchronized AuctionSession getSessionForItem(String itemId) {
        ensureInitialized();
        refreshFromStoreIfChanged();
        Item item = findItemInternal(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Auction item not found: " + itemId));

        return sessionRegistry.getOrCreateSession(
                item,
                bidHistoryByItemId.getOrDefault(itemId, List.of())
        );
    }

    public synchronized boolean refreshFromStoreIfChanged() {
        if (!initialized) {
            return false;
        }

        StoreSnapshot snapshot = dataManager.loadSnapshot();
        if (snapshot.version() == loadedStoreVersion) {
            return false;
        }

        applySnapshot(normalizeSnapshot(snapshot));
        return true;
    }

    public synchronized Item addSellerItem(
            String type,
            String itemName,
            String description,
            double startingPrice,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String extraText,
            int extraNumber,
            String sellerId
    ) {
        ensureInitialized();
        refreshFromStoreIfChanged();

        String prefix = type == null || type.isBlank()
                ? "ITEM"
                : type.trim().substring(0, Math.min(4, type.trim().length())).toUpperCase();
        Item item = ItemFactory.createItem(
                type,
                prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                itemName,
                description,
                startingPrice,
                startTime,
                endTime,
                extraText,
                extraNumber
        );
        item.setSellerId(sellerId);
        item.setApprovalStatus(ApprovalStatus.PENDING);
        items.add(item);
        persistCurrentState();
        return item;
    }

    public synchronized boolean updateApprovalStatus(String itemId, ApprovalStatus approvalStatus) {
        ensureInitialized();
        refreshFromStoreIfChanged();

        Optional<Item> existing = findItemInternal(itemId);
        if (existing.isEmpty()) {
            return false;
        }

        existing.get().setApprovalStatus(approvalStatus);
        persistCurrentState();
        return true;
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }

        applySnapshot(normalizeSnapshot(dataManager.loadSnapshot()));
        initialized = true;
    }

    private Optional<Item> findItemInternal(String itemId) {
        return items.stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst();
    }

    private Optional<Item> findItemById(List<Item> sourceItems, String itemId) {
        return sourceItems.stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst();
    }

    private Map<String, List<Bid>> copyBidHistory(Map<String, List<Bid>> source) {
        Map<String, List<Bid>> copy = new HashMap<>();
        for (Map.Entry<String, List<Bid>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }

    private StoreSnapshot normalizeSnapshot(StoreSnapshot snapshot) {
        if (!snapshot.store().getItems().isEmpty()) {
            return snapshot;
        }

        AuctionStore seededStore = new AuctionStore(AuctionSeedData.createDemoItems(), Map.of());
        return dataManager.saveStoreIfVersionMatches(seededStore, snapshot.version())
                .orElseGet(dataManager::loadSnapshot);
    }

    private void applySnapshot(StoreSnapshot snapshot) {
        items = new ArrayList<>(snapshot.store().getItems());
        bidHistoryByItemId = copyBidHistory(snapshot.store().getBidHistoryByItemId());
        loadedStoreVersion = snapshot.version();
        rebuildSessions();
    }

    private void rebuildSessions() {
        sessionRegistry.clear();
        sessionRegistry.preloadSessions(items, bidHistoryByItemId);
    }

    private void persistCurrentState() {
        StoreSnapshot snapshot = dataManager.saveStore(new AuctionStore(items, bidHistoryByItemId));
        applySnapshot(snapshot);
    }
}
