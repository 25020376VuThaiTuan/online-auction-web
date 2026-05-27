package org.example.service;

import org.example.auction.AuctionDepositResult;
import org.example.auction.AuctionSettlement;
import org.example.auction.BidValidationResult;
import org.example.auction.AuctionSessionRegistry;
import org.example.model.ApprovalStatus;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.model.Seller;
import org.example.model.User;
import org.example.viewmodel.AuctionEligibilityEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketplaceDashboardServiceCoverageExpansionTest {
    private static final String BIDDER_PIN = "2468";
    private static final String SELLER_PIN = "1357";

    private final AuthenticationService authenticationService = AuthenticationService.getInstance();
    private final MarketplaceDashboardService dashboardService = MarketplaceDashboardService.getInstance();

    @AfterEach
    void clearRegistry() {
        AuctionSessionRegistry.getInstance().clear();
    }

    @Test
    void serviceFacadeCoordinatesProfileCatalogWalletAuctionSettlementAndAuditFlow() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Bidder bidder = bidder("marketbid" + suffix, 800.0);
        Seller seller = seller("marketsell" + suffix);
        User admin = admin("marketadmin" + suffix);

        dashboardService.updateProfile(bidder, "Market Bidder Updated", "555-0300", "Market Street");
        dashboardService.updateAvatar(bidder, "https://example.test/market-avatar.png");
        assertEquals("Market Bidder Updated", bidder.getFullName());
        assertEquals("555-0300", bidder.getPhoneNumber());
        assertEquals("https://example.test/market-avatar.png", bidder.getAvatarUrl());

        dashboardService.setWalletPin(bidder, BIDDER_PIN);
        dashboardService.setWalletPin(seller, SELLER_PIN);
        assertTrue(dashboardService.hasWalletPin(bidder));
        assertEquals(800.0, dashboardService.getWallet(bidder, BIDDER_PIN).balance(), 0.001);

        Item item = dashboardService.addSellerItem(
                seller,
                "electronics",
                "Marketplace Coverage Camera " + suffix,
                "Coverage workflow item",
                100.0,
                LocalDateTime.now().minusMinutes(2),
                LocalDateTime.now().plusMinutes(20),
                "Coverage Brand",
                12
        );
        assertNotNull(item);
        assertEquals(seller.getId(), item.getSellerId());
        assertTrue(dashboardService.getSellerItems(seller).stream().anyMatch(candidate -> candidate.getId().equals(item.getId())));
        assertTrue(dashboardService.getPendingApprovalItems().stream().anyMatch(candidate -> candidate.getId().equals(item.getId())));
        assertTrue(dashboardService.updateItemApproval(item.getId(), ApprovalStatus.APPROVED));
        assertFalse(dashboardService.updateItemApproval("missing-" + suffix, ApprovalStatus.APPROVED));

        List<AuctionEligibilityEntry> entries = dashboardService.getAuctionEligibilityEntries(bidder);
        AuctionEligibilityEntry entry = entries.stream()
                .filter(candidate -> candidate.getItemId().equals(item.getId()))
                .findFirst()
                .orElseThrow();
        assertTrue(entry.isEligible());
        assertTrue(dashboardService.startAuction(seller, item.getId()));

        AuctionDepositResult deposit = dashboardService.confirmAuctionEntry(item.getId(), bidder, BIDDER_PIN);
        assertTrue(deposit.accepted(), deposit.message());
        assertTrue(dashboardService.hasConfirmedEntryDeposit(item.getId(), bidder));

        BidValidationResult bid = dashboardService.placeBidWithDeposit(item.getId(), bidder, 140.0, BIDDER_PIN);
        assertTrue(bid.accepted(), bid.message());
        assertTrue(dashboardService.registerAutoBidWithDeposit(item.getId(), bidder, 180.0, BIDDER_PIN));
        assertTrue(dashboardService.disableAutoBidWithDeposit(item.getId(), bidder, BIDDER_PIN));
        assertFalse(dashboardService.disableAutoBidWithDeposit(item.getId(), bidder, BIDDER_PIN));
        assertFalse(dashboardService.getBidHistory(item.getId()).isEmpty());

        assertTrue(dashboardService.finishAuction(seller, item.getId()));
        AuctionSettlement settlement = dashboardService.getSettlement(item.getId()).orElseThrow();
        assertEquals(bidder.getId(), settlement.getWinnerBidderId());
        assertFalse(dashboardService.getAllSettlements().isEmpty());

        dashboardService.admitWinnerResult(item.getId(), bidder, BIDDER_PIN);
        dashboardService.markGoodsShipped(item.getId(), seller, SELLER_PIN);
        AuctionSettlement paid = dashboardService.confirmGoodsReceived(item.getId(), bidder, BIDDER_PIN);
        assertEquals("PAYMENT_RELEASED", paid.getStatus().name());
        assertFalse(dashboardService.getNotifications(bidder).isEmpty());
        assertFalse(dashboardService.getNotifications(seller).isEmpty());

        assertTrue(dashboardService.getAllUsers().stream().anyMatch(user -> user.getId().equals(bidder.getId())));
        assertTrue(dashboardService.findUserById(bidder.getId()).isPresent());
        assertTrue(dashboardService.getWalletAuditTransactions(admin, bidder.getId()).size() >= 1);
        assertTrue(dashboardService.getSellerItems(admin).stream().anyMatch(candidate -> candidate.getId().equals(item.getId())));
    }

    private Bidder bidder(String username, double balance) {
        Bidder bidder = (Bidder) authenticationService.registerManualBidder(
                username,
                "secret",
                username + "@test.local",
                "Market Bidder " + username
        );
        bidder.setBalance(balance);
        authenticationService.updateUser(bidder);
        return bidder;
    }

    private Seller seller(String username) {
        return (Seller) authenticationService.registerManualSeller(
                username,
                "secret",
                username + "@test.local",
                "Market Seller " + username
        );
    }

    private User admin(String username) {
        User admin = authenticationService.registerManualBidder(
                username,
                "secret",
                username + "@test.local",
                "Market Admin " + username
        );
        authenticationService.updateUserRole(admin.getId(), "ADMIN");
        admin.setRole("ADMIN");
        return admin;
    }
}
