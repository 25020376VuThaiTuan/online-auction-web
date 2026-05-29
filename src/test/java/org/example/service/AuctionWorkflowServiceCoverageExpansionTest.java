package org.example.service;

import org.example.auction.AuctionSessionRegistry;
import org.example.auction.AuctionStatus;
import org.example.auction.BidValidationResult;
import org.example.dao.ItemDAO;
import org.example.model.ApprovalStatus;
import org.example.model.AuctionStore;
import org.example.model.AutoBid;
import org.example.model.Bid;
import org.example.model.Bidder;
import org.example.model.DataManager;
import org.example.model.Item;
import org.example.model.ItemFactory;
import org.example.viewmodel.AuctionListEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionWorkflowServiceCoverageExpansionTest {
    private static final String PIN = "2468";

    private final AuthenticationService authenticationService = AuthenticationService.getInstance();

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        AuctionSessionRegistry.getInstance().clear();
    }

    @Test
    void localCatalogQueriesFilterAndResolveItems() throws Exception {
        Item approved = item("WF-APPROVED", "Approved Camera", "SELLER-1", ApprovalStatus.APPROVED);
        Item pending = item("WF-PENDING", "Pending Camera", "SELLER-1", ApprovalStatus.PENDING);
        Item rejected = item("WF-REJECTED", "Rejected Camera", "SELLER-2", ApprovalStatus.REJECTED);
        AuctionWorkflowService service = serviceWithItems(approved, pending, rejected);

        List<AuctionListEntry> auctionEntries = service.getAuctionListEntries();

        assertEquals(List.of("WF-APPROVED"), auctionEntries.stream().map(AuctionListEntry::getItemId).toList());
        assertEquals(3, service.getAllItems().size());
        assertEquals(List.of("WF-PENDING"), service.getPendingApprovalItems().stream().map(Item::getId).toList());
        assertEquals(List.of("WF-APPROVED", "WF-PENDING"),
                service.getItemsForSeller("seller-1").stream().map(Item::getId).toList());
        assertEquals(List.of(),
                service.getItemsForSeller(null).stream().map(Item::getId).toList());
        assertTrue(service.findItemById("WF-APPROVED").isPresent());
        assertTrue(service.findItemById("missing").isEmpty());
        assertEquals("WF-APPROVED", service.getSummary("WF-APPROVED").itemId());
        assertEquals(0, service.getBidHistory("WF-APPROVED").size());
    }

    @Test
    void localCatalogMutationsCoverApprovalStartFinishAndAutoBidBranches() throws Exception {
        Item approved = item("WF-MUTATE", "Mutation Camera", "SELLER-1", ApprovalStatus.APPROVED);
        Item pending = item("WF-MUTATE-PENDING", "Pending Mutation", "SELLER-1", ApprovalStatus.PENDING);
        Bidder bidder = bidder("wfmutate", 500.0);
        AuctionWorkflowService service = serviceWithItems(approved, pending);

        assertFalse(service.updateApprovalStatus("missing", ApprovalStatus.APPROVED));
        assertTrue(service.updateApprovalStatus(pending.getId(), ApprovalStatus.APPROVED));
        assertEquals(ApprovalStatus.APPROVED, pending.getApprovalStatus());

        assertFalse(service.startAuction("missing"));
        assertTrue(service.startAuction(approved.getId()));
        assertTrue(service.finishAuction(approved.getId()));
        assertFalse(service.finishAuction(approved.getId()));
        assertFalse(service.startAuction(approved.getId()));
        assertFalse(service.updateApprovalStatus(approved.getId(), ApprovalStatus.REJECTED));

        assertFalse(service.registerAutoBid(approved.getId(), null, 100.0));
        assertFalse(service.registerAutoBid(approved.getId(), bidder, Double.NaN));
        assertFalse(service.registerAutoBid(approved.getId(), bidder, 100.0, -1.0));
        assertFalse(service.registerAutoBid("missing", bidder, 100.0));
        assertFalse(service.disableAutoBid(approved.getId(), null));
        assertFalse(service.disableAutoBid("", bidder));
        assertFalse(service.disableAutoBid("missing", bidder));
        assertFalse(service.disableAutoBid(pending.getId(), bidder));

        setAutoBids(service, pending.getId(), List.of(new AutoBid(1, bidder.getId(), pending.getId(), 200.0, 10.0)));

        assertTrue(service.disableAutoBid(pending.getId(), bidder));
        assertFalse(service.disableAutoBid(pending.getId(), bidder));

        Item added = service.addSellerItem(
                "vehicle",
                "Workflow Roadster",
                "Fast",
                100.0,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(30),
                "Model",
                10,
                "SELLER-2"
        );

        assertEquals(ApprovalStatus.PENDING, added.getApprovalStatus());
        assertEquals("SELLER-2", added.getSellerId());
        assertTrue(service.findItemById(added.getId()).isPresent());
    }

    @Test
    void localBidsAndAutoBidsUseRealDepositAndWalletState() throws Exception {
        Item item = item("WF-BID", "Bidding Camera", "SELLER-1", ApprovalStatus.APPROVED);
        AuctionWorkflowService service = serviceWithItems(item);
        Bidder bidder = bidder("wfbidder", 600.0);
        Bidder autoBidder = bidder("wfautobid", 600.0);
        WalletService.getInstance().setPin(bidder, PIN);
        WalletService.getInstance().setPin(autoBidder, PIN);
        service.startAuction(item.getId());

        assertTrue(AuctionSettlementService.getInstance()
                .lockEntryDeposit(item, service.getSummary(item.getId()), bidder)
                .accepted());
        assertTrue(AuctionSettlementService.getInstance()
                .lockEntryDeposit(item, service.getSummary(item.getId()), autoBidder)
                .accepted());
        @SuppressWarnings("unchecked")
        Map<String, List<AutoBid>> autoBidMap = (Map<String, List<AutoBid>>) field(service, "autoBidsByItemId");
        autoBidMap.remove(item.getId());
        assertTrue(service.registerAutoBid(item.getId(), autoBidder, 180.0, 20.0));
        assertTrue(service.registerAutoBid(item.getId(), autoBidder, 190.0, 20.0));

        BidValidationResult result = service.placeBid(item.getId(), bidder, 120.0);

        assertTrue(result.accepted(), result.message());
        assertTrue(service.getBidHistory(item.getId()).size() >= 1);
        assertTrue(service.getSummary(item.getId()).currentPrice() >= 120.0);
        assertFalse(service.registerAutoBid(item.getId(), bidder, 0.0));

        assertThrows(IllegalArgumentException.class, () -> service.placeBid("missing", bidder, 120.0));
    }

    @Test
    void extensionHelpersAndStaleDemoRefreshCoverLocalMaintenanceBranches() throws Exception {
        AuctionWorkflowService service = serviceWithItems();
        LocalDateTime previousEndTime = LocalDateTime.of(2026, 5, 27, 10, 0);
        BidValidationResult accepted = BidValidationResult.accepted(
                "Bid accepted.",
                120.0,
                120.0,
                130.0,
                AuctionStatus.RUNNING,
                previousEndTime
        );

        BidValidationResult extended = (BidValidationResult) method(
                "withFinalEffectiveEndTime",
                BidValidationResult.class,
                LocalDateTime.class,
                AuctionStatus.class
        ).invoke(service, accepted, previousEndTime.plusMinutes(2), AuctionStatus.FINISHED);

        assertTrue(extended.message().toLowerCase().contains("extended"));
        assertEquals(AuctionStatus.FINISHED, extended.status());
        assertEquals(previousEndTime.plusMinutes(2), extended.effectiveEndTime());
        assertEquals(accepted, method(
                "withFinalEffectiveEndTime",
                BidValidationResult.class,
                LocalDateTime.class,
                AuctionStatus.class
        ).invoke(service, accepted, previousEndTime, AuctionStatus.FINISHED));
        assertTrue((boolean) method("isExtended", LocalDateTime.class, LocalDateTime.class)
                .invoke(service, previousEndTime, previousEndTime.plusSeconds(1)));
        assertFalse((boolean) method("isExtended", LocalDateTime.class, LocalDateTime.class)
                .invoke(service, previousEndTime, previousEndTime));
        assertFalse((boolean) method("isExtended", LocalDateTime.class, LocalDateTime.class)
                .invoke(service, null, previousEndTime));
        method(
                "recordAuctionExtensionIfNeeded",
                ItemDAO.class,
                String.class,
                String.class,
                LocalDateTime.class,
                LocalDateTime.class
        ).invoke(service, null, "ITEM", "BID", previousEndTime, previousEndTime);

        IllegalStateException failure = (IllegalStateException) method("databaseFailure", String.class, SQLException.class)
                .invoke(service, "Database failed", new SQLException("broken"));
        assertTrue(failure.getMessage().contains("Database failed: broken"));

        LocalDateTime now = LocalDateTime.now();
        Item liveDemo = item("WF-LIVE-DEMO", "Live Demo", "", ApprovalStatus.APPROVED);
        liveDemo.setStartTime(now.minusMinutes(1));
        liveDemo.setEndTime(now.plusMinutes(30));
        assertFalse((boolean) method("refreshStaleLocalDemoAuctions").invoke(serviceWithItems(liveDemo)));

        Item sellerOwnedEnded = item("WF-SELLER-ENDED", "Seller Ended", "SELLER-2", ApprovalStatus.APPROVED);
        sellerOwnedEnded.setStartTime(now.minusHours(3));
        sellerOwnedEnded.setEndTime(now.minusHours(1));
        AuctionWorkflowService sellerOwnedService = serviceWithItems(sellerOwnedEnded);
        assertFalse((boolean) method("isLocalDemoItem", Item.class).invoke(sellerOwnedService, sellerOwnedEnded));
        assertFalse((boolean) method("refreshStaleLocalDemoAuctions").invoke(sellerOwnedService));

        Item staleDemo = item("WF-STALE-DEMO", "Stale Demo", "", ApprovalStatus.APPROVED);
        staleDemo.setStartTime(now.minusHours(3));
        staleDemo.setEndTime(now.minusHours(1));
        staleDemo.setCurrentPrice(999.0);
        AuctionWorkflowService staleDemoService = serviceWithItems(staleDemo);

        assertTrue((boolean) method("isLocalDemoItem", Item.class).invoke(staleDemoService, staleDemo));
        assertTrue((boolean) method("refreshStaleLocalDemoAuctions").invoke(staleDemoService));
        assertTrue(staleDemo.getEndTime().isAfter(LocalDateTime.now()));
        assertEquals(staleDemo.getStartingPrice(), staleDemo.getCurrentPrice(), 0.001);

        Item untrackedBidItem = item("WF-UNTRACKED-BID", "Untracked Bid", "", ApprovalStatus.APPROVED);
        Bid bid = new Bid("BID-UNTRACKED", "BIDDER", untrackedBidItem.getId(), 150.0, LocalDateTime.now());
        method("recordLocalBid", Item.class, Bid.class).invoke(serviceWithItems(), untrackedBidItem, bid);
        assertEquals(150.0, untrackedBidItem.getCurrentPrice(), 0.001);

        Item autoBidItem = item("WF-AUTOBID-MISSING-MAP", "Missing AutoBid Map", "", ApprovalStatus.APPROVED);
        AuctionWorkflowService autoBidService = serviceWithItems(autoBidItem);
        @SuppressWarnings("unchecked")
        Map<String, List<AutoBid>> autoBidsByItemId = (Map<String, List<AutoBid>>) field(autoBidService, "autoBidsByItemId");
        autoBidsByItemId.remove(autoBidItem.getId());
        assertFalse(autoBidService.disableAutoBid(autoBidItem.getId(), bidder("wfdisable", 100.0)));
        assertEquals(0.0, (double) method("autoBidAvailableBalance", String.class, AutoBid.class)
                .invoke(autoBidService, autoBidItem.getId(), new AutoBid(1, "", autoBidItem.getId(), 200.0, 10.0)), 0.001);
        assertEquals("BIDDER", method("leadingBidderId", List.class).invoke(
                autoBidService,
                List.of(new Bid("BID-LEAD", "BIDDER", autoBidItem.getId(), 150.0, LocalDateTime.now()))
        ));
    }

    private AuctionWorkflowService serviceWithItems(Item... items) throws Exception {
        AuctionWorkflowService service = new AuctionWorkflowService(null, null);
        setField(service, "dataManager", tempDataManager());
        setField(service, "initialized", true);
        setField(service, "usingLocalStore", true);
        setField(service, "items", new ArrayList<>(List.of(items)));
        Map<String, List<org.example.model.Bid>> bids = new ConcurrentHashMap<>();
        Map<String, List<AutoBid>> autoBids = new ConcurrentHashMap<>();
        for (Item item : items) {
            bids.put(item.getId(), new ArrayList<>());
            autoBids.put(item.getId(), new ArrayList<>());
        }
        setField(service, "bidHistoryByItemId", bids);
        setField(service, "autoBidsByItemId", autoBids);
        AuctionSessionRegistry.getInstance().clear();
        return service;
    }

    private DataManager tempDataManager() throws Exception {
        Constructor<DataManager> constructor = DataManager.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(tempDir.resolve("workflow-store.dat"));
    }

    private static void setAutoBids(AuctionWorkflowService service, String itemId, List<AutoBid> autoBids) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, List<AutoBid>> autoBidsByItemId = (Map<String, List<AutoBid>>) field(service, "autoBidsByItemId");
        autoBidsByItemId.put(itemId, new ArrayList<>(autoBids));
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = AuctionWorkflowService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = AuctionWorkflowService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Method method(String name, Class<?>... parameterTypes) throws Exception {
        Method method = AuctionWorkflowService.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private Item item(String id, String name, String sellerId, ApprovalStatus approvalStatus) {
        Item item = ItemFactory.createItem(
                "electronics",
                id,
                name,
                "Workflow test item",
                100.0,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plusMinutes(30),
                "Brand",
                12
        );
        item.setSellerId(sellerId);
        item.setApprovalStatus(approvalStatus);
        return item;
    }

    private Bidder bidder(String label, double balance) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Bidder bidder = (Bidder) authenticationService.registerManualBidder(
                label + "_" + suffix,
                "secret",
                label + "_" + suffix + "@test.local",
                "Workflow " + label + " " + suffix
        );
        bidder.setBalance(balance);
        authenticationService.updateUser(bidder);
        return bidder;
    }
}
