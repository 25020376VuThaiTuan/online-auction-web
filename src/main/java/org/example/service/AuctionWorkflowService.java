package org.example.service;

import org.example.auction.AuctionRules;
import org.example.auction.AuctionSession;
import org.example.auction.AuctionSessionRegistry;
import org.example.auction.AuctionSummary;
import org.example.auction.BidValidationResult;
import org.example.dao.BidDAO;
import org.example.dao.ItemDAO;
import org.example.model.ApprovalStatus;
import org.example.model.AutoBid;
import org.example.model.Bid;
import org.example.model.Item;
import org.example.model.ItemFactory;
import org.example.model.User;
import org.example.viewmodel.AuctionListEntry;

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
    
    private boolean initialized;
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
        refreshFromStoreIfChanged();
        Objects.requireNonNull(user, "user");

        Item item = findItemInternal(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Auction item not found: " + itemId));

        AuctionSession session = getSessionForItem(itemId);

        Bid bid = new Bid(
                "BID-" + UUID.randomUUID(),
                user.getUsername(),
                itemId,
                amount,
                LocalDateTime.now()
        );

        BidValidationResult result = session.submitBid(bid);
        if (!result.accepted()) {
            return result;
        }

        try (ItemDAO itemDAO = ItemDAO.fromEnvironment();
             BidDAO bidDAO = BidDAO.fromEnvironment()) {
            
            bidDAO.addBid(bid);
            itemDAO.updateCurrentPrice(itemId, item.getCurrentPrice());
            
            // Process auto-bids
            processAutoBids(item, session, bidDAO, itemDAO);

        } catch (SQLException e) {
            e.printStackTrace();
            return BidValidationResult.rejected("Database error: " + e.getMessage(), amount, 0, 0, null, null);
        }

        refreshFromStoreIfChanged();
        return result;
    }

    private void processAutoBids(Item item, AuctionSession session, BidDAO bidDAO, ItemDAO itemDAO) throws SQLException {
        boolean autoBidPlaced = true;
        while (autoBidPlaced) {
            autoBidPlaced = false;
            List<AutoBid> autoBids = bidDAO.getAllAutoBidsForItem(item.getId());
            double currentHighest = session.getCurrentHighestBid();
            double minNext = AuctionRules.minimumNextBid(currentHighest);
            
            for (AutoBid ab : autoBids) {
                // If the auto-bid limit is sufficient to place the minimum next bid
                if (ab.getMaxLimit() >= minNext && !ab.getBidderId().equals(session.getBids().get(session.getBids().size()-1).getBidderId())) {
                    Bid nextBid = new Bid("BID-" + UUID.randomUUID(), ab.getBidderId(), item.getId(), minNext, LocalDateTime.now());
                    BidValidationResult res = session.submitBid(nextBid);
                    if (res.accepted()) {
                        bidDAO.addBid(nextBid);
                        itemDAO.updateCurrentPrice(item.getId(), session.getCurrentHighestBid());
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
            e.printStackTrace();
            return false;
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
        
        try (ItemDAO itemDAO = ItemDAO.fromEnvironment()) {
            itemDAO.addItem(item, type, extraText, extraNumber);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
        
        refreshFromStoreIfChanged();
        return item;
    }

    public synchronized boolean updateApprovalStatus(String itemId, ApprovalStatus approvalStatus) {
        ensureInitialized();

        try (ItemDAO itemDAO = ItemDAO.fromEnvironment()) {
            itemDAO.updateApprovalStatus(itemId, approvalStatus);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
        
        refreshFromStoreIfChanged();
        return true;
    }
    
    public synchronized boolean registerAutoBid(String itemId, User user, double maxLimit) {
        try (BidDAO bidDAO = BidDAO.fromEnvironment()) {
            bidDAO.addOrUpdateAutoBid(new AutoBid(0, user.getUsername(), itemId, maxLimit));
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        refreshFromStoreIfChanged();
        initialized = true;
    }

    private Optional<Item> findItemInternal(String itemId) {
        return items.stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst();
    }

    private void rebuildSessions() {
        sessionRegistry.clear();
        sessionRegistry.preloadSessions(items, bidHistoryByItemId);
    }
}
