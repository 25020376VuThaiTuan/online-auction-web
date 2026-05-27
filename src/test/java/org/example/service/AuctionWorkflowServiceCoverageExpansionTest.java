package org.example.service;

import org.example.auction.AuctionSessionRegistry;
import org.example.auction.BidValidationResult;
import org.example.model.ApprovalStatus;
import org.example.model.AuctionStore;
import org.example.model.AutoBid;
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
import java.nio.file.Path;
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
        assertTrue(service.registerAutoBid(item.getId(), autoBidder, 180.0, 20.0));

        BidValidationResult result = service.placeBid(item.getId(), bidder, 120.0);

        assertTrue(result.accepted(), result.message());
        assertTrue(service.getBidHistory(item.getId()).size() >= 1);
        assertTrue(service.getSummary(item.getId()).currentPrice() >= 120.0);
        assertFalse(service.registerAutoBid(item.getId(), bidder, 0.0));

        assertThrows(IllegalArgumentException.class, () -> service.placeBid("missing", bidder, 120.0));
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
