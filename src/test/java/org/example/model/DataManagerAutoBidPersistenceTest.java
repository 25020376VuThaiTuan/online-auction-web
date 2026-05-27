package org.example.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ObjectInputFilter;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataManagerAutoBidPersistenceTest {
    @TempDir
    Path tempDir;

    @Test
    void saveLoadRoundTripIncludesAutoBidRegistrations() {
        Path dataFile = tempDir.resolve("data.dat");
        DataManager writer = new DataManager(dataFile);
        Item item = testItem("ITEM-AUTO-PERSIST");
        AutoBid autoBid = new AutoBid(7, "bidder-auto", item.getId(), 250.0, 15.0);
        Bid bid = new Bid("BID-1", "bidder-other", item.getId(), 120.0, LocalDateTime.now());

        writer.saveStore(new AuctionStore(
                List.of(item),
                Map.of(item.getId(), List.of(bid)),
                Map.of(item.getId(), List.of(autoBid))
        ));

        AuctionStore loaded = new DataManager(dataFile).loadStore();
        List<AutoBid> loadedAutoBids = loaded.getAutoBidsByItemId().get(item.getId());

        assertEquals(1, loaded.getItems().size());
        assertEquals(1, loaded.getBidHistoryByItemId().get(item.getId()).size());
        assertEquals(1, loadedAutoBids.size());
        assertEquals("bidder-auto", loadedAutoBids.getFirst().getBidderId());
        assertEquals(250.0, loadedAutoBids.getFirst().getMaxLimit(), 0.001);
        assertEquals(15.0, loadedAutoBids.getFirst().getBidIncrement(), 0.001);
    }

    @Test
    void legacyItemListFilesLoadWithEmptyAutoBidRegistrations() throws Exception {
        Path dataFile = tempDir.resolve("legacy-data.dat");
        Item item = testItem("ITEM-LEGACY-LIST");
        try (ObjectOutputStream outputStream = new ObjectOutputStream(Files.newOutputStream(dataFile))) {
            outputStream.writeObject(List.of(item));
        }

        AuctionStore loaded = new DataManager(dataFile).loadStore();

        assertEquals(1, loaded.getItems().size());
        assertTrue(loaded.getBidHistoryByItemId().isEmpty());
        assertTrue(loaded.getAutoBidsByItemId().isEmpty());
    }

    @Test
    void missingNullVersionedAndItemOnlyStoresUseRealPersistencePaths() {
        Path dataFile = tempDir.resolve("versioned-data.dat");
        DataManager manager = new DataManager(dataFile);

        StoreSnapshot missing = manager.loadSnapshot();
        assertTrue(missing.store().getItems().isEmpty());
        assertEquals(0L, missing.version());
        assertTrue(manager.loadItems().isEmpty());

        StoreSnapshot nullStore = manager.saveStore(null);
        assertTrue(nullStore.store().getItems().isEmpty());

        Item item = testItem("ITEM-VERSIONED");
        AuctionStore itemStore = new AuctionStore(List.of(item), Map.of(), Map.of());
        Optional<StoreSnapshot> mismatched = manager.saveStoreIfVersionMatches(itemStore, nullStore.version() + 1);
        assertTrue(mismatched.isEmpty());

        Optional<StoreSnapshot> matched = manager.saveStoreIfVersionMatches(itemStore, nullStore.version());
        assertTrue(matched.isPresent());
        assertEquals(1, matched.get().store().getItems().size());

        manager.saveItems(List.of(item));
        assertEquals(1, manager.loadItems().size());
    }

    @Test
    void corruptAndUnsupportedSerializedFilesLoadAsEmptyStores() throws Exception {
        Path corruptFile = tempDir.resolve("corrupt-data.dat");
        Files.writeString(corruptFile, "not a serialized store");
        assertTrue(new DataManager(corruptFile).loadStore().getItems().isEmpty());

        Path unsupportedFile = tempDir.resolve("unsupported-data.dat");
        try (ObjectOutputStream outputStream = new ObjectOutputStream(Files.newOutputStream(unsupportedFile))) {
            outputStream.writeObject("unsupported");
        }
        assertTrue(new DataManager(unsupportedFile).loadStore().getItems().isEmpty());
    }

    @Test
    void objectInputFilterAllowsModelAndJdkTypesButRejectsUnexpectedTypes() throws Exception {
        DataManager manager = new DataManager(tempDir.resolve("filter-data.dat"));

        assertEquals(ObjectInputFilter.Status.UNDECIDED, filterStatus(manager, null));
        assertEquals(ObjectInputFilter.Status.ALLOWED, filterStatus(manager, int.class));
        assertEquals(ObjectInputFilter.Status.ALLOWED, filterStatus(manager, Item[].class));
        assertEquals(ObjectInputFilter.Status.ALLOWED, filterStatus(manager, AuctionStore.class));
        assertEquals(ObjectInputFilter.Status.ALLOWED, filterStatus(manager, java.util.ArrayList.class));
        assertEquals(ObjectInputFilter.Status.ALLOWED, filterStatus(manager, LocalDateTime.class));
        assertEquals(ObjectInputFilter.Status.ALLOWED, filterStatus(manager, String.class));
        assertEquals(ObjectInputFilter.Status.REJECTED, filterStatus(manager, java.io.File.class));
    }

    private ObjectInputFilter.Status filterStatus(DataManager manager, Class<?> serialClass) throws Exception {
        Method method = DataManager.class.getDeclaredMethod("allowAuctionStoreClass", ObjectInputFilter.FilterInfo.class);
        method.setAccessible(true);
        return (ObjectInputFilter.Status) method.invoke(manager, new ObjectInputFilter.FilterInfo() {
            @Override
            public Class<?> serialClass() {
                return serialClass;
            }

            @Override
            public long arrayLength() {
                return -1;
            }

            @Override
            public long depth() {
                return 1;
            }

            @Override
            public long references() {
                return 1;
            }

            @Override
            public long streamBytes() {
                return 0;
            }
        });
    }

    private Item testItem(String itemId) {
        return ItemFactory.createItem(
                "electronics",
                itemId,
                "Persistence Test",
                "Test item",
                100.0,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(10),
                "Brand",
                1
        );
    }
}
