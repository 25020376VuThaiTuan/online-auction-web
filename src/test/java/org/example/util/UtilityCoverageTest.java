package org.example.util;

import org.example.model.Bid;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class UtilityCoverageTest {
    private static final AuctionCatalogFilters.EntryAdapter<CatalogTestEntry> CATALOG_TEST_ADAPTER =
            new AuctionCatalogFilters.EntryAdapter<>() {
                @Override
                public String itemId(CatalogTestEntry entry) {
                    return entry.id();
                }

                @Override
                public String itemName(CatalogTestEntry entry) {
                    return entry.name();
                }

                @Override
                public String status(CatalogTestEntry entry) {
                    return entry.status();
                }

                @Override
                public double currentPrice(CatalogTestEntry entry) {
                    return entry.currentPrice();
                }

                @Override
                public long remainingSeconds(CatalogTestEntry entry) {
                    return entry.remainingSeconds();
                }
            };

    @Test
    void accountInputValidatorNormalizesAndRejectsUnsafeRegistrationFields() {
        AccountInputValidator.RegistrationInput input = AccountInputValidator.validateRegistration(
                "  Test.User_1 ",
                "secret123",
                "User@Test.Local",
                " Test User "
        );

        assertEquals("test.user_1", input.username());
        assertEquals("User@Test.Local", input.email());
        assertEquals("Test User", input.fullName());
        assertEquals("nguyenvana", AccountInputValidator.normalizeIdentityLabel("Nguyen Van-A"));
        assertThrows(IllegalArgumentException.class,
                () -> AccountInputValidator.validateRegistration("bad user!", "secret123", "user@test.local", "Bad User"));
        assertThrows(IllegalArgumentException.class,
                () -> AccountInputValidator.validateRegistration("valid_user", " secret123", "user@test.local", "Valid User"));
    }

    @Test
    void moneyAndDisplayFormattersRoundPredictably() {
        assertEquals(12.35, MoneyUtils.roundCurrency(12.345), 0.001);
        assertEquals(0.0, MoneyUtils.roundCurrency(-10.0), 0.001);
        assertTrue(MoneyUtils.canCover(99.9999999, 100.0));
        assertFalse(MoneyUtils.canCover(99.98, 100.0));
        assertEquals("$12.35", AuctionDisplayFormatter.formatCurrency(12.345));
        assertEquals("Ended", AuctionDisplayFormatter.formatRemainingTime(0));
        assertEquals("No deadline", AuctionDisplayFormatter.formatRemainingTime(Long.MAX_VALUE));
    }

    @Test
    void currencyInputParserHandlesDecoratedFiniteAmounts() {
        assertEquals(1234.5, CurrencyInputParser.parseRequiredAmount(" $1,234.50 "), 0.001);
        assertEquals(0.0, CurrencyInputParser.parseOptionalAmount(" "), 0.001);
        assertEquals("12.35", CurrencyInputParser.formatAmountInput(12.345));
        assertThrows(NumberFormatException.class, () -> CurrencyInputParser.parseRequiredAmount(""));
        assertThrows(NumberFormatException.class, () -> CurrencyInputParser.parseRequiredAmount("NaN"));
        assertThrows(NumberFormatException.class, () -> CurrencyInputParser.parseRequiredAmount("Infinity"));
    }

    @Test
    void auctionCatalogFiltersSearchStatusWatchAndSortConsistently() {
        List<CatalogTestEntry> entries = List.of(
                new CatalogTestEntry("A-1", "Vintage Camera", "RUNNING", 120.0, 400L),
                new CatalogTestEntry("A-2", "Studio Lamp", "FINISHED", 40.0, 0L),
                new CatalogTestEntry("A-3", "Camera Lens", "OPEN", 75.0, 200L)
        );

        List<CatalogTestEntry> filtered = AuctionCatalogFilters.filterAndSort(
                entries,
                new AuctionCatalogFilters.FilterRequest(
                        "camera",
                        AuctionCatalogFilters.ALL_STATUSES,
                        AuctionCatalogFilters.SORT_WATCHED_FIRST,
                        true,
                        false
                ),
                CATALOG_TEST_ADAPTER,
                itemId -> "A-3".equals(itemId)
        );

        assertEquals(List.of("A-3", "A-1"), filtered.stream().map(CatalogTestEntry::id).toList());
        assertTrue(AuctionCatalogFilters.isOpenStatus(" running "));
        assertFalse(AuctionCatalogFilters.isOpenStatus("paid"));
        assertEquals("status-cancelled", AuctionCatalogFilters.statusStyleClass("cancelled"));
    }

    @Test
    void credentialHasherStoresSaltedVerifierInsteadOfPlainSecret() {
        String storedHash = CredentialHasher.hash("secret123");

        assertTrue(CredentialHasher.isHashed(storedHash));
        assertTrue(CredentialHasher.verify("secret123", storedHash));
        assertFalse(CredentialHasher.verify("wrong-secret", storedHash));
        assertFalse(storedHash.contains("secret123"));
    }

    @Test
    void bidChartSignatureIsStableForSameBidHistoryAndChangesForAmounts() {
        LocalDateTime time = LocalDateTime.of(2026, 5, 22, 10, 15, 30);
        List<Bid> first = List.of(new Bid("bid-1", "bidder-1", "item-1", 120.004, time));
        List<Bid> second = List.of(new Bid("bid-1", "bidder-1", "item-1", 120.004, time));
        List<Bid> changed = List.of(new Bid("bid-1", "bidder-1", "item-1", 130.0, time));

        assertEquals(BidChartUtils.signature(first), BidChartUtils.signature(second));
        assertNotEquals(BidChartUtils.signature(first), BidChartUtils.signature(changed));
    }

    @Test
    void windowProfilesProtectMinimumDesktopSizes() {
        WindowLayoutProfile dashboard = WindowLayoutProfile.forResource("/view/Dashboard.fxml");
        WindowLayoutProfile unknown = WindowLayoutProfile.forResource("/view/Missing.fxml");

        assertTrue(dashboard.preferredWidth() >= dashboard.minimumWidth());
        assertTrue(dashboard.preferredHeight() >= dashboard.minimumHeight());
        assertEquals(1120.0, unknown.preferredWidth(), 0.001);
        assertEquals(600.0, unknown.minimumHeight(), 0.001);
    }

    @Test
    void backgroundExecutorCreatesNamedDaemonThread() throws Exception {
        var executor = BackgroundExecutorFactory.newSingleThreadExecutor("test-worker");
        try {
            Future<String> result = executor.submit(() -> Thread.currentThread().getName()
                    + "|"
                    + Thread.currentThread().isDaemon());

            assertEquals("test-worker|true", result.get(2, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private record CatalogTestEntry(
            String id,
            String name,
            String status,
            double currentPrice,
            long remainingSeconds
    ) {
    }
}
