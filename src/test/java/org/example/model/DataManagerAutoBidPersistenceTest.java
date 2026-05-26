package org.example.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
