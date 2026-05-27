package org.example.model;

import org.example.auction.AuctionSeedData;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelValueObjectCoverageTest {
    @Test
    void autoBidConstructorsAndMutatorsExposeConfiguredValues() {
        AutoBid defaultIncrement = new AutoBid(1, "BIDDER-1", "ITEM-1", 500.0);
        assertEquals(0.0, defaultIncrement.getBidIncrement(), 0.001);

        AutoBid autoBid = new AutoBid(2, "BIDDER-2", "ITEM-2", 750.0, 25.0);
        autoBid.setId(3);
        autoBid.setBidderId("BIDDER-3");
        autoBid.setItemId("ITEM-3");
        autoBid.setMaxLimit(900.0);
        autoBid.setBidIncrement(50.0);

        assertEquals(3, autoBid.getId());
        assertEquals("BIDDER-3", autoBid.getBidderId());
        assertEquals("ITEM-3", autoBid.getItemId());
        assertEquals(900.0, autoBid.getMaxLimit(), 0.001);
        assertEquals(50.0, autoBid.getBidIncrement(), 0.001);
    }

    @Test
    void auctionStoreUsesDefensiveCopiesForNestedCollections() throws Exception {
        Item item = new TestItem("ITEM-1");
        Bid bid = new Bid("BID-1", "BIDDER-1", "ITEM-1", 120.0, LocalDateTime.now());
        AutoBid autoBid = new AutoBid(1, "BIDDER-1", "ITEM-1", 200.0);
        List<Item> items = new ArrayList<>(List.of(item));
        Map<String, List<Bid>> bids = new HashMap<>(Map.of("ITEM-1", new ArrayList<>(List.of(bid))));
        Map<String, List<AutoBid>> autoBids = new HashMap<>(Map.of("ITEM-1", new ArrayList<>(List.of(autoBid))));

        AuctionStore store = new AuctionStore(items, bids, autoBids);
        items.clear();
        bids.get("ITEM-1").clear();
        autoBids.get("ITEM-1").clear();

        assertEquals(1, store.getItems().size());
        assertEquals(1, store.getBidHistoryByItemId().get("ITEM-1").size());
        assertEquals(1, store.getAutoBidsByItemId().get("ITEM-1").size());

        List<Item> copiedItems = store.getItems();
        copiedItems.clear();
        assertEquals(1, store.getItems().size());

        AuctionStore reloaded = roundTrip(store);
        assertNotSame(store.getItems(), reloaded.getItems());
        assertEquals(1, reloaded.getItems().size());
        assertEquals(1, reloaded.getBidHistoryByItemId().get("ITEM-1").size());
        assertEquals(1, reloaded.getAutoBidsByItemId().get("ITEM-1").size());
    }

    @Test
    void emptyStoreAndSnapshotNormalizeNullInputs() {
        AuctionStore empty = AuctionStore.empty();
        StoreSnapshot snapshot = new StoreSnapshot(null, -10L);

        assertTrue(empty.getItems().isEmpty());
        assertTrue(empty.getBidHistoryByItemId().isEmpty());
        assertTrue(empty.getAutoBidsByItemId().isEmpty());
        assertTrue(snapshot.store().getItems().isEmpty());
        assertEquals(0L, snapshot.version());
    }

    @Test
    void itemStateHelpersNormalizeNullableValues() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 26, 10, 0);
        LocalDateTime end = start.plusHours(2);
        Item item = new TestItem("ITEM-2", start, end);

        item.setCurrentPrice(150.0);
        item.setSellerId("SELLER-1");
        item.setApprovalStatus(null);

        assertEquals("ITEM-2", item.getId());
        assertEquals("Test item", item.getItemName());
        assertEquals("Description", item.getDescription());
        assertEquals(100.0, item.getStartingPrice(), 0.001);
        assertEquals(150.0, item.getCurrentPrice(), 0.001);
        assertEquals("SELLER-1", item.getSellerId());
        assertEquals(ApprovalStatus.APPROVED, item.getApprovalStatus());
        assertTrue(item.isApproved());
        assertEquals(start, item.getStartTime());
        assertEquals(end, item.getEndTime());
        assertFalse(item.hasStarted(start.minusSeconds(1)));
        assertTrue(item.hasStarted(start));
        assertFalse(item.isEnded(start.plusMinutes(30)));
        assertTrue(item.isEnded(end));
        assertEquals(7_200L, item.getRemainingSeconds(start));
        assertEquals("26/05/2026 12:00", item.getEndTimeString());

        item.setStartTime(null);
        item.setEndTime(null);
        item.setApprovalStatus(ApprovalStatus.REJECTED);

        assertTrue(item.hasStarted(null));
        assertFalse(item.isEnded(null));
        assertEquals(Long.MAX_VALUE, item.getRemainingSeconds(null));
        assertEquals("N/A", item.getEndTimeString());
        assertFalse(item.isApproved());
    }

    @Test
    void displayMethodsWriteRoleAndItemSummaries() {
        assertConsoleContains("Role: Bidder", () -> new Bidder("BIDDER-1", "bidder", "hash", "b@test.local", 10.0).displayRole());
        assertConsoleContains("Role:", () -> new Seller("SELLER-1", "seller", "hash", "s@test.local").displayRole());
        assertConsoleContains("Role:", () -> new Admin("ADMIN-1", "admin", "hash", "a@test.local").displayRole());
        assertConsoleContains("Mona Lisa", () -> new Art(
                "ART-1",
                "Mona Lisa",
                "Painting",
                100.0,
                100.0,
                null,
                null,
                "Da Vinci",
                1503
        ).displayInfo());
        assertConsoleContains("Camera", () -> new Electronics(
                "ELEC-1",
                "Camera",
                "Mirrorless",
                100.0,
                100.0,
                null,
                null,
                "Brand",
                24
        ).displayInfo());
        assertConsoleContains("Roadster", () -> new Vehicle(
                "VEH-1",
                "Roadster",
                "Convertible",
                100.0,
                100.0,
                null,
                null,
                "Model S",
                1_200
        ).displayInfo());
    }

    @Test
    void seedDataAndLegacyAuctionManagerExposeDemoCatalog() {
        List<Item> demoItems = AuctionSeedData.createDemoItems();

        assertEquals(3, demoItems.size());
        assertEquals(List.of("ELEC-001", "ART-001", "VEH-001"), demoItems.stream().map(Item::getId).toList());
        assertTrue(demoItems.stream().allMatch(Item::isApproved));
        assertTrue(demoItems.stream().allMatch(item -> item.getEndTime() != null));

        AuctionManager manager = AuctionManager.getInstance();
        int sizeBefore = manager.getItems().size();
        manager.addItem(demoItems.getFirst());

        assertSame(manager, AuctionManager.getInstance());
        assertSame(demoItems.getFirst(), manager.getItems().get(sizeBefore));
    }

    @Test
    void legacyModelMainRunsThroughConsoleVerificationFlow() {
        assertConsoleContains("TEST 1", () -> Main.main(new String[0]));
    }

    private static AuctionStore roundTrip(AuctionStore store) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(store);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (AuctionStore) input.readObject();
        }
    }

    private static void assertConsoleContains(String expected, Runnable action) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(error, true, StandardCharsets.UTF_8));
            action.run();
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        String combinedOutput = output.toString(StandardCharsets.UTF_8) + error.toString(StandardCharsets.UTF_8);
        assertTrue(combinedOutput.contains(expected));
    }

    private static final class TestItem extends Item {
        private TestItem(String id) {
            this(id, null, null);
        }

        private TestItem(String id, LocalDateTime startTime, LocalDateTime endTime) {
            super(id, "Test item", "Description", 100.0, 100.0, startTime, endTime);
        }

        @Override
        public void displayInfo() {
            System.out.println(getItemName());
        }
    }
}
