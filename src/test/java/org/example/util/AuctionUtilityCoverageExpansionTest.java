package org.example.util;

import org.example.model.Bid;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionUtilityCoverageExpansionTest {
    private static final AuctionCatalogFilters.EntryAdapter<CatalogEntry> ADAPTER =
            new AuctionCatalogFilters.EntryAdapter<>() {
                @Override
                public String itemId(CatalogEntry entry) {
                    return entry.id();
                }

                @Override
                public String itemName(CatalogEntry entry) {
                    return entry.name();
                }

                @Override
                public String status(CatalogEntry entry) {
                    return entry.status();
                }

                @Override
                public double currentPrice(CatalogEntry entry) {
                    return entry.price();
                }

                @Override
                public long remainingSeconds(CatalogEntry entry) {
                    return entry.remainingSeconds();
                }
            };

    @Test
    void displayFormatterHandlesDateTimeAndRemainingTimeBranches() {
        LocalDateTime value = LocalDateTime.of(2026, 5, 27, 9, 15, 30);

        assertEquals("", AuctionDisplayFormatter.formatDateTime(null));
        assertEquals("27/05/2026 09:15:30", AuctionDisplayFormatter.formatDateTime(value));
        assertEquals("1d 01h 01m 01s", AuctionDisplayFormatter.formatRemainingTime(90_061L));
        assertEquals("1h 01m 01s", AuctionDisplayFormatter.formatRemainingTime(3_661L));
        assertEquals("0m 59s", AuctionDisplayFormatter.formatRemainingTime(59L));
    }

    @Test
    void catalogOptionsAndNormalizersExposeStableUiValues() {
        assertEquals("All statuses", AuctionCatalogFilters.statusOptions().getFirst());
        assertTrue(AuctionCatalogFilters.statusOptions().contains("CANCELLED"));
        assertEquals("Ending soonest", AuctionCatalogFilters.sortOptions().getFirst());
        assertTrue(AuctionCatalogFilters.sortOptions().contains(AuctionCatalogFilters.SORT_NAME));
        assertEquals("", AuctionCatalogFilters.normalizeText(null));
        assertEquals("camera", AuctionCatalogFilters.normalizeText(" Camera "));
        assertEquals("", AuctionCatalogFilters.normalizeStatus(null));
        assertEquals("RUNNING", AuctionCatalogFilters.normalizeStatus(" running "));
        assertEquals("status-open", AuctionCatalogFilters.statusStyleClass("unknown"));
        assertEquals("status-running", AuctionCatalogFilters.statusStyleClass("running"));
        assertEquals("status-finished", AuctionCatalogFilters.statusStyleClass("finished"));
        assertEquals("status-paid", AuctionCatalogFilters.statusStyleClass("paid"));
        assertTrue(AuctionCatalogFilters.isOpenStatus(null));
        assertFalse(AuctionCatalogFilters.isOpenStatus("cancelled"));
    }

    @Test
    void catalogFiltersHandleNullInputsAndEverySortMode() {
        List<CatalogEntry> entries = List.of(
                new CatalogEntry("A-1", "Beta Camera", "RUNNING", 200.0, 30L),
                new CatalogEntry("A-2", "Alpha Lamp", "OPEN", 50.0, 20L),
                new CatalogEntry("A-3", "Gamma Lens", "FINISHED", 100.0, 10L)
        );

        assertTrue(AuctionCatalogFilters.filterAndSort(null, null, ADAPTER, null).isEmpty());
        assertTrue(AuctionCatalogFilters.filterAndSort(List.of(), null, ADAPTER, null).isEmpty());

        List<CatalogEntry> defaultSorted = AuctionCatalogFilters.filterAndSort(entries, null, ADAPTER, null);
        assertEquals(List.of("A-3", "A-2", "A-1"), ids(defaultSorted));

        List<CatalogEntry> priceLow = AuctionCatalogFilters.filterAndSort(
                entries,
                new AuctionCatalogFilters.FilterRequest("", null, AuctionCatalogFilters.SORT_PRICE_LOW, false, false),
                ADAPTER,
                null
        );
        assertEquals(List.of("A-2", "A-3", "A-1"), ids(priceLow));

        List<CatalogEntry> priceHigh = AuctionCatalogFilters.filterAndSort(
                entries,
                new AuctionCatalogFilters.FilterRequest("", AuctionCatalogFilters.ALL_STATUSES, AuctionCatalogFilters.SORT_PRICE_HIGH, false, false),
                ADAPTER,
                null
        );
        assertEquals(List.of("A-1", "A-3", "A-2"), ids(priceHigh));

        List<CatalogEntry> byName = AuctionCatalogFilters.filterAndSort(
                entries,
                new AuctionCatalogFilters.FilterRequest("", AuctionCatalogFilters.ALL_STATUSES, AuctionCatalogFilters.SORT_NAME, false, false),
                ADAPTER,
                null
        );
        assertEquals(List.of("A-2", "A-1", "A-3"), ids(byName));

        List<CatalogEntry> watchedOnly = AuctionCatalogFilters.filterAndSort(
                entries,
                new AuctionCatalogFilters.FilterRequest("", AuctionCatalogFilters.ALL_STATUSES, AuctionCatalogFilters.SORT_WATCHED_FIRST, false, true),
                ADAPTER,
                "A-1"::equals
        );
        assertEquals(List.of("A-1"), ids(watchedOnly));

        List<CatalogEntry> openRunning = AuctionCatalogFilters.filterAndSort(
                entries,
                new AuctionCatalogFilters.FilterRequest("a", AuctionCatalogFilters.ALL_STATUSES, AuctionCatalogFilters.SORT_ENDING_SOON, true, false),
                ADAPTER,
                null
        );
        assertEquals(List.of("A-2", "A-1"), ids(openRunning));
    }

    @Test
    void bidChartSignatureHandlesNullAndSparseBidFields() {
        LocalDateTime time = LocalDateTime.of(2026, 5, 27, 9, 15, 30);

        assertEquals("", BidChartUtils.signature(null));
        assertEquals("", BidChartUtils.signature(List.of()));
        assertEquals(
                ":12.35:|BID-2:20.0:2026-05-27T09:15:30|",
                BidChartUtils.signature(List.of(
                        new Bid(null, null, "ITEM-1", 12.345, null),
                        new Bid("BID-2", "BIDDER-2", "ITEM-1", 20.0, time)
                ))
        );
    }

    private static List<String> ids(List<CatalogEntry> entries) {
        return entries.stream().map(CatalogEntry::id).toList();
    }

    private record CatalogEntry(String id, String name, String status, double price, long remainingSeconds) {
    }
}
