package org.example.service;

import org.example.auction.AuctionRules;
import org.example.auction.AuctionSeedData;
import org.example.auction.AuctionSession;
import org.example.auction.AuctionSessionRegistry;
import org.example.auction.AuctionStatus;
import org.example.auction.AuctionSummary;
import org.example.auction.BidValidationResult;
import org.example.dao.BidDAO;
import org.example.dao.DatabaseConfig;
import org.example.dao.ItemDAO;
import org.example.model.ApprovalStatus;
import org.example.model.AuctionStore;
import org.example.model.AutoBid;
import org.example.model.Bid;
import org.example.model.DataManager;
import org.example.model.Item;
import org.example.model.ItemFactory;
import org.example.model.User;
import org.example.viewmodel.AuctionListEntry;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class AuctionWorkflowService {
    private static final AuctionWorkflowService INSTANCE = new AuctionWorkflowService();

    private final AuctionSessionRegistry sessionRegistry = AuctionSessionRegistry.getInstance();
    private final DataManager dataManager = DataManager.getInstance();
    
    private boolean initialized;
    private boolean usingLocalStore;
    private List<Item> items = new ArrayList<>();
    private Map<String, List<Bid>> bidHistoryByItemId = new HashMap<>();
    private Map<String, List<AutoBid>> autoBidsByItemId = new HashMap<>();

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
        refreshFromStoreIfChanged();
        Objects.requireNonNull(user, "user");

        Item item = findItemInternal(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Auction item not found: " + itemId));

        AuctionSession session = getSessionForItem(itemId);

        Bid bid = new Bid(
                newBidId(),
                user.getId(),
                itemId,
                amount,
                LocalDateTime.now()
        );

        BidValidationResult result = session.submitBid(bid);
        if (!result.accepted()) {
            return result;
        }

        if (usingLocalStore) {
            recordLocalBid(item, bid);
            processLocalAutoBids(item, session, bid.getBidderId());
            persistLocalStore();
            return result;
        }

        try {
            persistAcceptedBid(item, bid, session);
        } catch (SQLException e) {
            refreshFromStoreIfChanged();
            throw databaseFailure("Database bid persistence failed", e);
        }

        refreshFromStoreIfChanged();
        return result;
    }

    private void persistAcceptedBid(Item item, Bid bid, AuctionSession session) throws SQLException {
        DatabaseConfig config = DatabaseConfig.fromEnvironment();
        try (Connection connection = config.openConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                if (originalAutoCommit) {
                    connection.setAutoCommit(false);
                }
                try (ItemDAO itemDAO = new ItemDAO(connection);
                     BidDAO bidDAO = new BidDAO(connection)) {
                    bidDAO.addBid(bid);
                    itemDAO.updateCurrentPrice(item.getId(), item.getCurrentPrice());
                    processAutoBids(item, session, bidDAO, itemDAO, bid.getBidderId());
                }
                if (originalAutoCommit) {
                    connection.commit();
                }
            } catch (SQLException e) {
                if (originalAutoCommit) {
                    connection.rollback();
                }
                throw e;
            } finally {
                if (originalAutoCommit) {
                    connection.setAutoCommit(true);
                }
            }
        }
    }

    private void processAutoBids(
            Item item,
            AuctionSession session,
            BidDAO bidDAO,
            ItemDAO itemDAO,
            String triggerBidderId
    ) throws SQLException {
        boolean autoBidPlaced = true;
        while (autoBidPlaced) {
            autoBidPlaced = false;
            List<AutoBid> autoBids = bidDAO.getAllAutoBidsForItem(item.getId());
            double currentHighest = session.getCurrentHighestBid();
            List<Bid> sessionBids = session.getBids();
            if (sessionBids.isEmpty()) {
                return;
            }
            String leadingBidderId = sessionBids.get(sessionBids.size() - 1).getBidderId();
            
            for (AutoBid ab : autoBids) {
                if (shouldTriggerAutoBid(ab, triggerBidderId, leadingBidderId)) {
                    double nextAmount = nextAutoBidAmount(currentHighest, ab);
                    if (nextAmount <= 0.0) {
                        continue;
                    }
                    Bid nextBid = new Bid(newBidId(), ab.getBidderId(), item.getId(), nextAmount, LocalDateTime.now());
                    BidValidationResult res = session.submitBid(nextBid);
                    if (res.accepted()) {
                        bidDAO.addBid(nextBid);
                        itemDAO.updateCurrentPrice(item.getId(), session.getCurrentHighestBid());
                        triggerBidderId = ab.getBidderId();
                        autoBidPlaced = true;
                        break; // Re-evaluate all auto-bids after a successful bid
                    }
                }
            }
        }
    }

    public synchronized AuctionSession getSessionForItem(String itemId) {
        ensureInitialized();
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

        if (usingLocalStore) {
            return false;
        }

        return refreshFromDatabase();
    }

    private boolean refreshFromDatabase() {
        if (!databaseEnabled()) {
            return false;
        }

        try (ItemDAO itemDAO = ItemDAO.fromEnvironment();
             BidDAO bidDAO = BidDAO.fromEnvironment()) {
            
            items = itemDAO.getAllItems();
            bidHistoryByItemId.clear();
            for (Item item : items) {
                bidHistoryByItemId.put(item.getId(), bidDAO.getBidsForItem(item.getId()));
            }
            rebuildSessions();
            return true;
        } catch (SQLException e) {
            throw databaseFailure("Database auction refresh failed", e);
        }
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

        if (usingLocalStore) {
            items.add(item);
            bidHistoryByItemId.putIfAbsent(item.getId(), new ArrayList<>());
            persistLocalStore();
            rebuildSessions();
            return item;
        }
        
        try (ItemDAO itemDAO = ItemDAO.fromEnvironment()) {
            itemDAO.addItem(item, type, extraText, extraNumber);
        } catch (SQLException e) {
            throw databaseFailure("Database item save failed", e);
        }
        
        refreshFromStoreIfChanged();
        return item;
    }

    public synchronized boolean updateApprovalStatus(String itemId, ApprovalStatus approvalStatus) {
        ensureInitialized();

        Optional<Item> existingItem = findItemInternal(itemId);
        if (existingItem.isEmpty() || getSessionForItem(itemId).getStatus().isFinished()) {
            return false;
        }

        if (usingLocalStore) {
            existingItem.get().setApprovalStatus(approvalStatus);
            persistLocalStore();
            return true;
        }

        try (ItemDAO itemDAO = ItemDAO.fromEnvironment()) {
            itemDAO.updateApprovalStatus(itemId, approvalStatus);
        } catch (SQLException e) {
            throw databaseFailure("Database approval update failed", e);
        }
        
        refreshFromStoreIfChanged();
        return true;
    }
    
    public synchronized boolean registerAutoBid(String itemId, User user, double maxLimit) {
        return registerAutoBid(itemId, user, maxLimit, 0.0);
    }

    public synchronized boolean registerAutoBid(String itemId, User user, double maxLimit, double bidIncrement) {
        ensureInitialized();
        if (user == null || !Double.isFinite(maxLimit) || maxLimit <= 0.0
                || !Double.isFinite(bidIncrement) || bidIncrement < 0.0) {
            return false;
        }

        if (usingLocalStore) {
            List<AutoBid> autoBids = autoBidsByItemId.computeIfAbsent(itemId, ignored -> new ArrayList<>());
            autoBids.removeIf(autoBid -> autoBid.getBidderId().equals(user.getId()));
            autoBids.add(new AutoBid(autoBids.size() + 1, user.getId(), itemId, maxLimit, bidIncrement));
            return true;
        }

        try (BidDAO bidDAO = BidDAO.fromEnvironment()) {
            bidDAO.addOrUpdateAutoBid(new AutoBid(0, user.getId(), itemId, maxLimit, bidIncrement));
            return true;
        } catch (SQLException e) {
            throw databaseFailure("Database auto-bid save failed", e);
        }
    }

    public synchronized boolean startAuction(String itemId) {
        ensureInitialized();
        refreshFromStoreIfChanged();

        Optional<Item> item = findItemInternal(itemId);
        if (item.isEmpty()) {
            return false;
        }

        AuctionSession session = getSessionForItem(itemId);
        if (session.getStatus().isFinished()) {
            return false;
        }

        session.startAuction();
        persistAuctionWindow(item.get(), AuctionStatus.RUNNING);
        return true;
    }

    public synchronized boolean finishAuction(String itemId) {
        ensureInitialized();
        refreshFromStoreIfChanged();

        Optional<Item> item = findItemInternal(itemId);
        if (item.isEmpty()) {
            return false;
        }

        AuctionSession session = getSessionForItem(itemId);
        if (session.getStatus().isFinished()) {
            return false;
        }

        session.finishAuction();
        persistAuctionWindow(item.get(), AuctionStatus.FINISHED);
        return true;
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        if (!refreshFromDatabase()) {
            initializeLocalStore();
        }
    }

    private Optional<Item> findItemInternal(String itemId) {
        return items.stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst();
    }

    private String newBidId() {
        return UUID.randomUUID().toString();
    }

    private void processLocalAutoBids(Item item, AuctionSession session, String triggerBidderId) {
        boolean autoBidPlaced = true;
        while (autoBidPlaced) {
            autoBidPlaced = false;
            List<AutoBid> autoBids = autoBidsByItemId.getOrDefault(item.getId(), List.of());
            double currentHighest = session.getCurrentHighestBid();
            List<Bid> sessionBids = session.getBids();
            if (sessionBids.isEmpty()) {
                return;
            }
            String leadingBidderId = sessionBids.get(sessionBids.size() - 1).getBidderId();

            for (AutoBid autoBid : autoBids) {
                if (shouldTriggerAutoBid(autoBid, triggerBidderId, leadingBidderId)) {
                    double nextAmount = nextAutoBidAmount(currentHighest, autoBid);
                    if (nextAmount <= 0.0) {
                        continue;
                    }
                    Bid nextBid = new Bid(newBidId(), autoBid.getBidderId(), item.getId(), nextAmount, LocalDateTime.now());
                    BidValidationResult result = session.submitBid(nextBid);
                    if (result.accepted()) {
                        recordLocalBid(item, nextBid);
                        triggerBidderId = autoBid.getBidderId();
                        autoBidPlaced = true;
                        break;
                    }
                }
            }
        }
    }

    static boolean shouldTriggerAutoBid(AutoBid autoBid, String triggerBidderId, String leadingBidderId) {
        if (autoBid == null || autoBid.getBidderId() == null || autoBid.getBidderId().isBlank()) {
            return false;
        }
        String autoBidderId = autoBid.getBidderId();
        return !autoBidderId.equals(triggerBidderId) && !autoBidderId.equals(leadingBidderId);
    }

    static double nextAutoBidAmount(double currentHighest, AutoBid autoBid) {
        if (autoBid == null) {
            return 0.0;
        }
        double minNext = AuctionRules.minimumNextBid(currentHighest);
        if (autoBid.getMaxLimit() < minNext) {
            return 0.0;
        }
        double requiredIncrement = AuctionRules.minimumIncrement(currentHighest);
        double configuredIncrement = autoBid.getBidIncrement();
        double effectiveIncrement = configuredIncrement > 0.0
                ? Math.max(configuredIncrement, requiredIncrement)
                : requiredIncrement;
        double requestedAmount = roundCurrency(currentHighest + effectiveIncrement);
        return roundCurrency(Math.min(autoBid.getMaxLimit(), requestedAmount));
    }

    private static double roundCurrency(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }

    private void recordLocalBid(Item item, Bid bid) {
        bidHistoryByItemId.computeIfAbsent(item.getId(), ignored -> new ArrayList<>()).add(bid);
        item.setCurrentPrice(Math.max(item.getCurrentPrice(), bid.getAmount()));
    }

    private void initializeLocalStore() {
        usingLocalStore = true;
        AuctionStore store = dataManager.loadStore();
        items = store.getItems();
        bidHistoryByItemId = store.getBidHistoryByItemId();
        boolean storeChanged = false;

        if (items.isEmpty()) {
            items = new ArrayList<>(AuctionSeedData.createDemoItems());
            for (Item item : items) {
                bidHistoryByItemId.putIfAbsent(item.getId(), new ArrayList<>());
            }
            storeChanged = true;
        }

        for (Item item : items) {
            if ("U-SEL-001".equalsIgnoreCase(item.getSellerId())) {
                item.setSellerId("");
                storeChanged = true;
            }
            if (!bidHistoryByItemId.containsKey(item.getId())) {
                bidHistoryByItemId.put(item.getId(), new ArrayList<>());
                storeChanged = true;
            }
        }

        if (refreshStaleLocalDemoAuctions()) {
            storeChanged = true;
        }
        if (storeChanged) {
            persistLocalStore();
        }
        rebuildSessions();
    }

    private boolean refreshStaleLocalDemoAuctions() {
        LocalDateTime now = LocalDateTime.now();
        boolean hasLiveApprovedAuction = items.stream()
                .filter(Item::isApproved)
                .anyMatch(item -> AuctionRules.resolveStatus(item.getStartTime(), item.getEndTime(), now) != AuctionStatus.FINISHED);
        if (hasLiveApprovedAuction) {
            return false;
        }

        List<Item> restartableDemoItems = items.stream()
                .filter(Item::isApproved)
                .filter(this::isLocalDemoItem)
                .filter(item -> bidHistoryByItemId.getOrDefault(item.getId(), List.of()).isEmpty())
                .toList();
        if (restartableDemoItems.isEmpty()) {
            return false;
        }

        for (int index = 0; index < restartableDemoItems.size(); index++) {
            Item item = restartableDemoItems.get(index);
            item.setStartTime(now.minusMinutes(5L + index));
            item.setEndTime(now.plusHours(2L + index));
            item.setCurrentPrice(item.getStartingPrice());
        }
        return true;
    }

    private boolean isLocalDemoItem(Item item) {
        String sellerId = item.getSellerId();
        return sellerId.isBlank();
    }

    private void persistLocalStore() {
        if (usingLocalStore) {
            dataManager.saveStore(new AuctionStore(items, bidHistoryByItemId));
        }
    }

    private void persistAuctionWindow(Item item, AuctionStatus status) {
        if (usingLocalStore) {
            persistLocalStore();
            return;
        }

        try (ItemDAO itemDAO = ItemDAO.fromEnvironment()) {
            itemDAO.updateAuctionWindow(item.getId(), item.getStartTime(), item.getEndTime(), status.name());
        } catch (SQLException e) {
            throw databaseFailure("Database auction window update failed", e);
        }
        refreshFromStoreIfChanged();
    }

    private boolean databaseEnabled() {
        String problem = DatabaseConfig.environmentProblem();
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        return DatabaseConfig.hasEnvironmentConfig();
    }

    private IllegalStateException databaseFailure(String operation, SQLException e) {
        return new IllegalStateException(operation + ": " + e.getMessage(), e);
    }

    private void rebuildSessions() {
        sessionRegistry.clear();
        sessionRegistry.preloadSessions(items, bidHistoryByItemId);
    }
}
