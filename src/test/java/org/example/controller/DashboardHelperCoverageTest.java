package org.example.controller;

import org.example.model.Bid;
import org.example.model.Bidder;
import org.example.model.Seller;
import org.example.model.WalletSummary;
import org.example.model.WalletTransaction;
import org.example.service.MarketplaceDashboardService;
import org.example.state.ApplicationSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DashboardHelperCoverageTest {
    private final ApplicationSession session = ApplicationSession.getInstance();

    @AfterEach
    void tearDown() {
        session.logout();
    }

    @Test
    void dashboardFormattersHandleNullsAndSpecificStyles() {
        LocalDateTime time = LocalDateTime.of(2026, 5, 27, 9, 15, 30);
        Bid bid = new Bid("BID-1", "BIDDER-1", "ITEM-1", 120.5, time);
        WalletTransaction transaction = new WalletTransaction(
                "TX-1",
                "USER-1",
                "LOCK_DEPOSIT",
                25.0,
                100.0,
                75.0,
                "ITEM-1",
                "Entry deposit",
                time
        );
        WalletTransaction transactionWithoutNote = new WalletTransaction(
                "TX-2",
                "USER-1",
                "RELEASE",
                25.0,
                75.0,
                100.0,
                "ITEM-1",
                " ",
                time
        );

        assertEquals("N/A", DashboardFormatters.formatBidNotificationTime(null));
        assertEquals("27/05 09:15:30", DashboardFormatters.formatBidNotificationTime(time));
        assertEquals("N/A", DashboardFormatters.formatDateTime(null));
        assertEquals("27/05/2026 09:15", DashboardFormatters.formatDateTime(time));
        assertEquals("BIDDER-1 -> $120.50 at 27/05 09:15:30", DashboardFormatters.formatSellerBidHistoryLine(bid));
        assertEquals(
                "27/05/2026 09:15 - USER-1 - LOCK DEPOSIT - $25.00 - balance $75.00 - Entry deposit",
                DashboardFormatters.walletAuditLine(transaction)
        );
        assertEquals(
                "27/05/2026 09:15 - USER-1 - RELEASE - $25.00 - balance $100.00",
                DashboardFormatters.walletAuditLine(transactionWithoutNote)
        );
        assertEquals("eligible-entered", DashboardFormatters.eligibleStyleClass("Entered"));
        assertEquals("eligible-ready", DashboardFormatters.eligibleStyleClass(" Can Enter "));
        assertEquals("eligible-blocked", DashboardFormatters.eligibleStyleClass("No"));
        assertEquals("", DashboardFormatters.value(null));
        assertEquals("text", DashboardFormatters.value(" text "));
    }

    @Test
    void walletSummariesPreferCurrentBidderFinancialsWithoutLosingDetails() {
        Bidder bidder = new Bidder("BIDDER-1", "bidder", "hash", "bidder@test.local", 200.0);
        bidder.lockDeposit("ITEM-1", 50.0);
        WalletSummary snapshot = wallet("BIDDER-1", 10.0, 1.0, 9.0, true, "snapshot");
        WalletSummary detail = wallet("BIDDER-1", 20.0, 2.0, 18.0, false, "detail");

        WalletSummary current = DashboardWalletSummaries.withCurrentFinancials(snapshot, bidder);
        assertEquals(200.0, current.balance(), 0.001);
        assertEquals(50.0, current.lockedBalance(), 0.001);
        assertEquals(150.0, current.availableBalance(), 0.001);
        assertEquals(snapshot.transactions(), current.transactions());

        WalletSummary dashboard = DashboardWalletSummaries.dashboardWallet(bidder, snapshot, detail);
        assertEquals(200.0, dashboard.balance(), 0.001);
        assertEquals(50.0, dashboard.lockedBalance(), 0.001);
        assertEquals(150.0, dashboard.availableBalance(), 0.001);
        assertEquals(detail.transactions(), dashboard.transactions());
        assertEquals(detail.linkedAccounts(), dashboard.linkedAccounts());
        assertEquals(snapshot.pinSet(), dashboard.pinSet());
    }

    @Test
    void walletSummariesReturnOriginalWhenUserOrWalletDoesNotMatch() {
        Seller seller = new Seller("SELLER-1", "seller", "hash", "seller@test.local");
        WalletSummary wallet = wallet("BIDDER-1", 10.0, 1.0, 9.0, true, "wallet");
        WalletSummary other = wallet("OTHER", 20.0, 2.0, 18.0, false, "other");

        assertSame(wallet, DashboardWalletSummaries.withCurrentFinancials(wallet, null));
        assertSame(wallet, DashboardWalletSummaries.withCurrentFinancials(wallet, seller));
        assertSame(wallet, DashboardWalletSummaries.withCurrentFinancials(wallet, new Bidder("OTHER", "other", "hash", "o@test.local", 1.0)));
        assertSame(wallet, DashboardWalletSummaries.dashboardWallet(seller, wallet, other));
        assertSame(wallet, DashboardWalletSummaries.mergeFinancials(wallet, other));
        assertSame(wallet, DashboardWalletSummaries.mergeFinancials(wallet, null));
        assertSame(wallet, DashboardWalletSummaries.mergeFinancials(null, wallet));
    }

    @Test
    void bidderNameResolverUsesCurrentUserBeforeServiceLookup() {
        Bidder bidder = new Bidder("BIDDER-1", "bidder", "hash", "bidder@test.local", 100.0);
        bidder.setFullName("Current Bidder");
        session.login(bidder);

        DashboardBidderNameResolver resolver = new DashboardBidderNameResolver(
                session,
                MarketplaceDashboardService.getInstance()
        );

        assertEquals("Unknown bidder", resolver.displayName(" "));
        assertEquals("Current Bidder", resolver.displayName("BIDDER-1"));
    }

    private static WalletSummary wallet(String userId, double balance, double locked, double available, boolean pinSet, String note) {
        return new WalletSummary(
                userId,
                balance,
                locked,
                available,
                pinSet,
                List.of(),
                List.of(new WalletTransaction("TX-" + note, userId, "TYPE", 1.0, 0.0, 1.0, null, note, null))
        );
    }
}
