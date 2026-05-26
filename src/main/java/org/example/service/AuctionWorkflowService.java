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
import org.example.model.WalletSummary;
import org.example.viewmodel.AuctionListEntry;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class AuctionWorkflowService {
    private static final AuctionWorkflowService INSTANCE = new AuctionWorkflowService();

    private final AuctionSessionRegistry sessionRegistry = AuctionSessionRegistry.getInstance();
    private final DataManager dataManager = DataManager.getInstance();
    private final AuthenticationService authenticationService = AuthenticationService.getInstance();
    private final AuctionSettlementService settlementService = AuctionSettlementService.getInstance();
    private final WalletService walletService = WalletService.getInstance();
    private final AutoBidPolicy autoBidPolicy;
    private final BidAuthorizationPolicy bidAuthorizationPolicy;
    
    private final ConcurrentHashMap<String, ReentrantLock> itemLocks = new ConcurrentHashMap<>();
    
    private volatile boolean initialized;
    private boolean usingLocalStore;
    private List<Item> items = new ArrayList<>();
    private Map<String, List<Bid>> bidHistoryByItemId = new ConcurrentHashMap<>();
    private Map<String, List<AutoBid>> autoBidsByItemId = new ConcurrentHashMap<>();

    private AuctionWorkflowService() {
        this(AutoBidPolicy.defaultPolicy(), BidAuthorizationPolicy.defaultPolicy());
    }

    AuctionWorkflowService(AutoBidPolicy autoBidPolicy, BidAuthorizationPolicy bidAuthorizationPolicy) {
        this.autoBidPolicy = Objects.requireNonNullElse(autoBidPolicy, AutoBidPolicy.defaultPolicy());
        this.bidAuthorizationPolicy = Objects.requireNonNullElse(
                bidAuthorizationPolicy,
                BidAuthorizationPolicy.defaultPolicy()
        );
    }

    public static AuctionWorkflowService getInstance() {
        return INSTANCE;
    }

    private ReentrantLock getLock(String itemId) {
        return itemLocks.computeIfAbsent(itemId, k -> new ReentrantLock(true));
    }

    public List<AuctionListEntry> getAuctionListEntries() {
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

    public List<Item> getAllItems() {
        ensureInitialized();
        refreshFromStoreIfChanged();
        return new ArrayList<>(items);
    }

    public List<Item> getPendingApprovalItems() {
        ensureInitialized();
        refreshFromStoreIfChanged();
        return items.stream()
                .filter(item -> item.getApprovalStatus() == ApprovalStatus.PENDING)
                .toList();
    }

    public List<Item> getItemsForSeller(String sellerId) {
        ensureInitialized();
        refreshFromStoreIfChanged();
        return items.stream()
                .filter(item -> item.getSellerId().equalsIgnoreCase(sellerId == null ? "" : sellerId))
                .toList();
    }

    public Optional<Item> findItemById(String itemId) {
        ensureInitialized();
        refreshFromStoreIfChanged();
        return findItemInternal(itemId);
    }

    public AuctionSummary getSummary(String itemId) {
        ensureInitialized();
        refreshFromStoreIfChanged();
        return getSessionForItem(itemId).getSummary();
    }

    public List<Bid> getBidHistory(String itemId) {
        ensureInitialized();
        refreshFromStoreIfChanged();
        return getSessionForItem(itemId).getBids();
    }

    public BidValidationResult placeBid(String itemId, User user, double amount) {
        ensureInitialized();
        refreshFromStoreIfChanged();
        Objects.requireNonNull(user, "user");

        ReentrantLock lock = getLock(itemId);
        lock.lock();
        try {
            Item item = findItemInternal(itemId)
                    .orElseThrow(() -> new IllegalArgumentException("Auction item not found: " + itemId));

            AuctionSession session = getSessionForItem(itemId);
            double availableBalance = bidCapacity(itemId, user);
            BidValidationResult authorizationFailure = bidAuthorizationPolicy.failureOrNull(
                    item,
                    session.getSummary(),
                    user,
                    amount,
                    hasEntryDeposit(itemId, user),
                    availableBalance
            );
            if (authorizationFailure != null) {
                return authorizationFailure;
            }

            Bid bid = new Bid(
                    newBidId(),
                    user.getId(),
                    itemId,
                    amount,
                    LocalDateTime.now()
            );

            if (usingLocalStore) {
                String previousLeadingBidderId = leadingBidderId(session.getBids());
                BidValidationResult result = session.submitBid(bid);
                if (!result.accepted()) {
                    return result;
                }
                recordLocalBid(item, bid);
                processLocalAutoBids(item, session, bid.getBidderId());
                BidValidationResult finalResult = withFinalEffectiveEndTime(
                        result,
                        session.getEndTime(),
                        session.getStatus()
                );
                
                // Update bid hold for the final highest bidder in local store
                List<Bid> finalBids = session.getBids();
                if (!finalBids.isEmpty()) {
                    settlementService.updateBidHold(item, finalBids.get(finalBids.size() - 1), previousLeadingBidderId);
                }
                
                persistLocalStore();
                return finalResult;
            }

            try {
                return persistAcceptedBid(item, bid);
            } catch (SQLException e) {
                refreshFromStoreIfChanged();
                throw databaseFailure("Database bid persistence failed", e);
            }
        } finally {
            lock.unlock();
        }
    }

    private BidValidationResult persistAcceptedBid(Item item, Bid bid) throws SQLException {
        try (Connection connection = DatabaseConfig.fromEnvironment().openConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            BidValidationResult acceptedResult;
            try {
                if (originalAutoCommit) {
                    connection.setAutoCommit(false);
                }
                try (ItemDAO itemDAO = new ItemDAO(connection);
                     BidDAO bidDAO = new BidDAO(connection)) {
                    itemDAO.lockAuctionForUpdate(item.getId());
                    Item lockedItem = itemDAO.getItemById(item.getId());
                    if (lockedItem == null) {
                        throw new SQLException("Auction item not found: " + item.getId(), "42S02", 1146);
                    }
                    var extensionConfig = itemDAO.getAuctionExtensionConfig(lockedItem.getId());
                    AuctionSession lockedSession = new AuctionSession(
                            lockedItem,
                            lockedItem.getStartingPrice(),
                            lockedItem.getEndTime(),
                            bidDAO.getBidsForItem(lockedItem.getId()),
                            extensionConfig
                    );
                    String previousLeadingBidderId = leadingBidderId(lockedSession.getBids());
                    LocalDateTime previousEndTime = lockedSession.getEndTime();
                    BidValidationResult result = lockedSession.submitBid(bid);
                    if (!result.accepted()) {
                        if (originalAutoCommit) {
                            connection.commit();
                        }
                        return result;
                    }
                    acceptedResult = result;
                    bidDAO.addBid(bid);
                    recordAuctionExtensionIfNeeded(
                            itemDAO,
                            lockedItem.getId(),
                            bid.getId(),
                            previousEndTime,
                            lockedSession.getEndTime()
                    );
                    itemDAO.updateAuctionProgress(
                            lockedItem.getId(),
                            lockedSession.getCurrentHighestBid(),
                            lockedSession.getEndTime(),
                            lockedSession.getStatus().name()
                    );
                    processAutoBids(lockedItem, lockedSession, bidDAO, itemDAO, bid.getBidderId());
                    acceptedResult = withFinalEffectiveEndTime(
                            acceptedResult,
                            lockedSession.getEndTime(),
                            lockedSession.getStatus()
                    );

                    // Update bid hold for the final winner of this bidding round using the same transaction
                    List<Bid> finalBids = lockedSession.getBids();
                    if (!finalBids.isEmpty()) {
                        settlementService.updateBidHold(
                                connection,
                                lockedItem,
                                finalBids.get(finalBids.size() - 1),
                                previousLeadingBidderId
                        );
                    }
                }
                if (originalAutoCommit) {
                    connection.commit();
                }
                refreshFromStoreIfChanged();
                return acceptedResult;
            } catch (SQLException e) {
                if (originalAutoCommit) {
                    connection.rollback();
                }
                throw e;
            } catch (RuntimeException e) {
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
        int attemptsRemaining = 1_000;
        while (autoBidPlaced && attemptsRemaining-- > 0) {
            autoBidPlaced = false;
            List<AutoBid> autoBids = bidDAO.getAllAutoBidsForItem(item.getId());
            double currentHighest = session.getCurrentHighestBid();
            List<Bid> sessionBids = session.getBids();
            if (sessionBids.isEmpty()) {
                return;
            }
            String leadingBidderId = sessionBids.get(sessionBids.size() - 1).getBidderId();
            
            for (AutoBid ab : autoBids) {
                if (autoBidPolicy.shouldTrigger(ab, triggerBidderId, leadingBidderId)) {
                    double nextAmount = autoBidPolicy.nextBidAmount(
                            currentHighest,
                            ab,
                            autoBidAvailableBalance(item.getId(), ab)
                    );
                    if (nextAmount <= 0.0) {
                        continue;
                    }
                    Bid nextBid = new Bid(newBidId(), ab.getBidderId(), item.getId(), nextAmount, LocalDateTime.now());
                    LocalDateTime previousEndTime = session.getEndTime();
                    BidValidationResult res = session.submitBid(nextBid);
                    if (res.accepted()) {
                        bidDAO.addBid(nextBid);
                        recordAuctionExtensionIfNeeded(
                                itemDAO,
                                item.getId(),
                                nextBid.getId(),
                                previousEndTime,
                                session.getEndTime()
                        );
                        itemDAO.updateAuctionProgress(
                                item.getId(),
                                session.getCurrentHighestBid(),
                                session.getEndTime(),
                                session.getStatus().name()
                        );
                        triggerBidderId = ab.getBidderId();
                        autoBidPlaced = true;
                        break; // Re-evaluate all auto-bids after a successful bid
                    }
                }
            }
        }
        if (attemptsRemaining <= 0) {
            throw new SQLException("Auto-bid processing did not settle for item: " + item.getId());
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
        assert type != null;
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
            autoBidsByItemId.putIfAbsent(item.getId(), new ArrayList<>());
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

        Optional<Item> item = findItemInternal(itemId);
        if (item.isEmpty()) {
            return false;
        }
        double availableBalance = bidCapacity(itemId, user);
        BidValidationResult authorizationFailure = bidAuthorizationPolicy.failureOrNull(
                item.get(),
                getSessionForItem(itemId).getSummary(),
                user,
                maxLimit,
                hasEntryDeposit(itemId, user),
                availableBalance
        );
        if (authorizationFailure != null) {
            return false;
        }

        if (usingLocalStore) {
            List<AutoBid> autoBids = autoBidsByItemId.computeIfAbsent(itemId, ignored -> new ArrayList<>());
            autoBids.removeIf(autoBid -> autoBid.getBidderId().equals(user.getId()));
            autoBids.add(new AutoBid(autoBids.size() + 1, user.getId(), itemId, maxLimit, bidIncrement));
            persistLocalStore();
            return true;
        }

        try (Connection connection = DatabaseConfig.fromEnvironment().openConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                if (originalAutoCommit) {
                    connection.setAutoCommit(false);
                }
                try (ItemDAO itemDAO = new ItemDAO(connection);
                     BidDAO bidDAO = new BidDAO(connection)) {
                    itemDAO.lockAuctionForUpdate(itemId);
                    bidDAO.addOrUpdateAutoBid(new AutoBid(0, user.getId(), itemId, maxLimit, bidIncrement));
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
            return true;
        } catch (SQLException e) {
            throw databaseFailure("Database auto-bid save failed", e);
        }
    }

    public synchronized boolean disableAutoBid(String itemId, User user) {
        ensureInitialized();
        if (user == null || itemId == null || itemId.isBlank()) {
            return false;
        }

        Optional<Item> item = findItemInternal(itemId);
        if (item.isEmpty()) {
            return false;
        }

        if (usingLocalStore) {
            List<AutoBid> autoBids = autoBidsByItemId.computeIfAbsent(itemId, ignored -> new ArrayList<>());
            boolean removed = autoBids.removeIf(autoBid -> autoBid.getBidderId().equals(user.getId()));
            if (removed) {
                persistLocalStore();
            }
            return removed;
        }

        try (Connection connection = DatabaseConfig.fromEnvironment().openConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                if (originalAutoCommit) {
                    connection.setAutoCommit(false);
                }
                boolean removed;
                try (ItemDAO itemDAO = new ItemDAO(connection);
                     BidDAO bidDAO = new BidDAO(connection)) {
                    itemDAO.lockAuctionForUpdate(itemId);
                    removed = bidDAO.deleteAutoBid(user.getId(), itemId);
                }
                if (originalAutoCommit) {
                    connection.commit();
                }
                return removed;
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
        } catch (SQLException e) {
            throw databaseFailure("Database auto-bid disable failed", e);
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
        int attemptsRemaining = 1_000;
        while (autoBidPlaced && attemptsRemaining-- > 0) {
            autoBidPlaced = false;
            List<AutoBid> autoBids = autoBidsByItemId.getOrDefault(item.getId(), List.of());
            double currentHighest = session.getCurrentHighestBid();
            List<Bid> sessionBids = session.getBids();
            if (sessionBids.isEmpty()) {
                return;
            }
            String leadingBidderId = sessionBids.get(sessionBids.size() - 1).getBidderId();

            for (AutoBid autoBid : autoBids) {
                if (autoBidPolicy.shouldTrigger(autoBid, triggerBidderId, leadingBidderId)) {
                    double nextAmount = autoBidPolicy.nextBidAmount(
                            currentHighest,
                            autoBid,
                            autoBidAvailableBalance(item.getId(), autoBid)
                    );
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
        if (attemptsRemaining <= 0) {
            throw new IllegalStateException("Auto-bid processing did not settle for item: " + item.getId());
        }
    }

    static boolean shouldTriggerAutoBid(AutoBid autoBid, String triggerBidderId, String leadingBidderId) {
        return AutoBidPolicy.defaultPolicy().shouldTrigger(autoBid, triggerBidderId, leadingBidderId);
    }

    static double nextAutoBidAmount(double currentHighest, AutoBid autoBid) {
        return AutoBidPolicy.defaultPolicy().nextBidAmount(currentHighest, autoBid);
    }

    static double nextAutoBidAmount(double currentHighest, AutoBid autoBid, double availableBalance) {
        return AutoBidPolicy.defaultPolicy().nextBidAmount(currentHighest, autoBid, availableBalance);
    }

    static BidValidationResult bidAuthorizationFailure(
            Item item,
            AuctionSummary summary,
            User user,
            double amount,
            boolean hasEntryDeposit,
            double availableBalance
    ) {
        return BidAuthorizationPolicy.defaultPolicy().failureOrNull(
                item,
                summary,
                user,
                amount,
                hasEntryDeposit,
                availableBalance
        );
    }

    private static double roundCurrency(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }

    private boolean hasEntryDeposit(String itemId, User user) {
        return user != null && settlementService.hasEntryDeposit(itemId, user);
    }

    private double autoBidAvailableBalance(String itemId, AutoBid autoBid) {
        if (autoBid == null || autoBid.getBidderId() == null || autoBid.getBidderId().isBlank()) {
            return 0.0;
        }
        Optional<User> bidder = authenticationService.findById(autoBid.getBidderId());
        if (bidder.isEmpty()) {
            return 0.0;
        }
        double availableBalance = bidCapacity(itemId, bidder.get());
        return settlementService.hasEntryDeposit(itemId, bidder.get()) ? availableBalance : 0.0;
    }

    private double bidCapacity(String itemId, User user) {
        if (user == null) {
            return 0.0;
        }
        WalletSummary wallet = walletService.getWalletSnapshot(user);
        return roundCurrency(wallet.availableBalance() + settlementService.lockedEntryDeposit(itemId, user));
    }

    private String leadingBidderId(List<Bid> bids) {
        if (bids == null || bids.isEmpty()) {
            return null;
        }
        return bids.get(bids.size() - 1).getBidderId();
    }

    private void recordAuctionExtensionIfNeeded(
            ItemDAO itemDAO,
            String itemId,
            String triggerBidId,
            LocalDateTime previousEndTime,
            LocalDateTime newEndTime
    ) throws SQLException {
        if (isExtended(previousEndTime, newEndTime)) {
            itemDAO.recordAuctionExtension(itemId, triggerBidId, previousEndTime, newEndTime);
        }
    }

    private BidValidationResult withFinalEffectiveEndTime(
            BidValidationResult result,
            LocalDateTime finalEndTime,
            AuctionStatus finalStatus
    ) {
        if (result == null || !result.accepted() || finalEndTime == null || finalEndTime.equals(result.effectiveEndTime())) {
            return result;
        }

        String message = result.message() == null ? "" : result.message();
        if (isExtended(result.effectiveEndTime(), finalEndTime) && !message.toLowerCase().contains("extended")) {
            message = "Bid accepted. Auction end time was extended.";
        }

        return BidValidationResult.accepted(
                message,
                result.attemptedAmount(),
                result.currentPrice(),
                result.minimumAllowedBid(),
                finalStatus == null ? result.status() : finalStatus,
                finalEndTime
        );
    }

    private boolean isExtended(LocalDateTime previousEndTime, LocalDateTime newEndTime) {
        return previousEndTime != null && newEndTime != null && newEndTime.isAfter(previousEndTime);
    }

    private void recordLocalBid(Item item, Bid bid) {
        bidHistoryByItemId.computeIfAbsent(item.getId(), ignored -> new ArrayList<>()).add(bid);
        item.setCurrentPrice(Math.max(item.getCurrentPrice(), bid.getAmount()));
    }

    private void initializeLocalStore() {
        usingLocalStore = true;
        AuctionStore store = dataManager.loadStore();
        items = store.getItems();
        bidHistoryByItemId = new ConcurrentHashMap<>(store.getBidHistoryByItemId());
        autoBidsByItemId = new ConcurrentHashMap<>(store.getAutoBidsByItemId());
        boolean storeChanged = false;

        if (items.isEmpty()) {
            items = new ArrayList<>(AuctionSeedData.createDemoItems());
            for (Item item : items) {
                bidHistoryByItemId.putIfAbsent(item.getId(), new ArrayList<>());
                autoBidsByItemId.putIfAbsent(item.getId(), new ArrayList<>());
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
            if (!autoBidsByItemId.containsKey(item.getId())) {
                autoBidsByItemId.put(item.getId(), new ArrayList<>());
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
            dataManager.saveStore(new AuctionStore(items, bidHistoryByItemId, autoBidsByItemId));
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
