package org.example.service;

import org.example.auction.AuctionDepositResult;
import org.example.auction.AuctionSettlement;
import org.example.auction.AuctionSettlementStatus;
import org.example.auction.AuctionStatus;
import org.example.auction.AuctionSummary;
import org.example.auction.UserNotification;
import org.example.model.Admin;
import org.example.model.Bid;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.model.ItemFactory;
import org.example.model.User;
import org.example.model.WalletTransaction;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionSettlementServiceTest {
    private final AuthenticationService authenticationService = AuthenticationService.getInstance();
    private final AuctionSettlementService settlementService = AuctionSettlementService.getInstance();
    private final WalletService walletService = WalletService.getInstance();
    private final Admin auditAdmin = admin("audit");

    @Test
    void finalizeAuctionNotifiesWinnerAndOtherAttendeesWithoutEarlyPayout() {
        Bidder winner = bidder("winner_notify");
        Bidder loser = bidder("loser_notify");
        User seller = seller("seller_notify");
        topUp(winner, 10_000.0);
        topUp(loser, 10_000.0);

        String itemName = "Notification Test " + UUID.randomUUID().toString().substring(0, 8);
        Item item = ItemFactory.createItem(
                "electronics",
                "TEST-" + UUID.randomUUID().toString().substring(0, 8),
                itemName,
                "Test item",
                100.0,
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().minusSeconds(1),
                "Brand",
                12
        );
        item.setSellerId(seller.getId());

        AuctionSummary entrySummary = new AuctionSummary(
                item.getId(),
                item.getItemName(),
                AuctionStatus.RUNNING,
                item.getCurrentPrice(),
                110.0,
                60L,
                0,
                null
        );
        AuctionDepositResult winnerDeposit = settlementService.lockEntryDeposit(item, entrySummary, winner);
        AuctionDepositResult loserDeposit = settlementService.lockEntryDeposit(item, entrySummary, loser);
        assertTrue(winnerDeposit.accepted());
        assertTrue(loserDeposit.accepted());

        List<Bid> bidHistory = List.of(
                new Bid("BID-L-" + UUID.randomUUID(), loser.getId(), item.getId(), 120.0, LocalDateTime.now().minusMinutes(2)),
                new Bid("BID-W-" + UUID.randomUUID(), winner.getId(), item.getId(), 140.0, LocalDateTime.now().minusMinutes(1))
        );
        item.setCurrentPrice(140.0);
        double adminFeeTotalBefore = transactionTotal("ADMIN_FEE");
        AuctionSummary finishedSummary = new AuctionSummary(
                item.getId(),
                item.getItemName(),
                AuctionStatus.FINISHED,
                item.getCurrentPrice(),
                150.0,
                0L,
                bidHistory.size(),
                winner.getId()
        );

        Optional<AuctionSettlement> settlement = settlementService.finalizeAuction(item, finishedSummary, bidHistory);

        assertTrue(settlement.isPresent());
        assertEquals(140.0 * 1.03, settlement.get().getTotalBuyerDue(), 0.001);
        assertTrue(hasNotification(winner, itemName, "You have won this session"));
        assertTrue(hasNotification(loser, itemName, "Another bidder won this session"));
        assertEquals(adminFeeTotalBefore, transactionTotal("ADMIN_FEE"), 0.001);
        assertEquals(0.0, walletService.getWalletSnapshot(seller).balance(), 0.001);
    }

    @Test
    void confirmedReceiptReleasesRecordedRemainingPaymentToSeller() {
        Bidder winner = bidder("winner_release");
        User seller = seller("seller_release");
        topUp(winner, 10_000.0);

        String itemName = "Settlement Release " + UUID.randomUUID().toString().substring(0, 8);
        Item item = ItemFactory.createItem(
                "electronics",
                "TEST-" + UUID.randomUUID().toString().substring(0, 8),
                itemName,
                "Test item",
                100.0,
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().minusSeconds(1),
                "Brand",
                12
        );
        item.setSellerId(seller.getId());

        AuctionSummary entrySummary = new AuctionSummary(
                item.getId(),
                item.getItemName(),
                AuctionStatus.RUNNING,
                item.getCurrentPrice(),
                110.0,
                60L,
                0,
                null
        );
        assertTrue(settlementService.lockEntryDeposit(item, entrySummary, winner).accepted());

        List<Bid> bidHistory = List.of(
                new Bid("BID-W-" + UUID.randomUUID(), winner.getId(), item.getId(), 140.0, LocalDateTime.now().minusMinutes(1))
        );
        item.setCurrentPrice(140.0);
        double adminFeeTotalBefore = transactionTotal("ADMIN_FEE");
        AuctionSummary finishedSummary = new AuctionSummary(
                item.getId(),
                item.getItemName(),
                AuctionStatus.FINISHED,
                item.getCurrentPrice(),
                150.0,
                0L,
                bidHistory.size(),
                winner.getId()
        );

        AuctionSettlement settlement = settlementService.finalizeAuction(item, finishedSummary, bidHistory).orElseThrow();
        double expectedSellerRelease = settlement.getWinningBidAmount();
        double expectedAdminFee = settlement.getBuyerPremiumAmount();
        assertEquals(adminFeeTotalBefore, transactionTotal("ADMIN_FEE"), 0.001);
        assertEquals(0.0, walletService.getWalletSnapshot(seller).balance(), 0.001);

        settlementService.admitWinnerResult(item.getId(), winner);
        assertTrue(hasNotification(seller, itemName, "Buyer admitted result"));
        long winnerHoldCountBeforeShipping = countTransactions(winner, "BID_HOLD");
        settlementService.markGoodsShipped(item.getId(), seller);
        assertEquals(settlement.getRemainingPaymentDue(), walletService.getWalletSnapshot(winner).lockedBalance(), 0.001);
        assertTrue(countTransactions(winner, "BID_HOLD") > winnerHoldCountBeforeShipping);
        long winnerPaymentCountBeforeReceipt = countTransactions(winner, "PAYMENT");
        AuctionSettlement releasedSettlement = settlementService.confirmGoodsReceived(item.getId(), winner);

        assertEquals(AuctionSettlementStatus.PAYMENT_RELEASED, releasedSettlement.getStatus());
        assertEquals(expectedSellerRelease, releasedSettlement.getSellerReleasedAmount(), 0.001);
        assertEquals(0.0, releasedSettlement.getLockedRemainingPayment(), 0.001);
        assertTrue(countTransactions(winner, "PAYMENT") > winnerPaymentCountBeforeReceipt);
        assertEquals(expectedSellerRelease, walletService.getWalletSnapshot(seller).balance(), 0.001);
        assertEquals(expectedAdminFee, transactionTotal("ADMIN_FEE") - adminFeeTotalBefore, 0.001);
    }

    private boolean hasNotification(User user, String itemName, String expectedText) {
        return settlementService.getNotificationsFor(user).stream()
                .map(UserNotification::getDisplayText)
                .anyMatch(line -> line.contains(itemName) && line.contains(expectedText));
    }

    private void topUp(Bidder bidder, double amount) {
        bidder.setBalance(bidder.getBalance() + amount);
        authenticationService.updateUser(bidder);
    }

    private Bidder bidder(String label) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return (Bidder) authenticationService.registerManualBidder(
                label + "_" + suffix,
                "password",
                label + "_" + suffix + "@example.test",
                "Test Bidder " + suffix
        );
    }

    private User seller(String label) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return authenticationService.registerManualSeller(
                label + "_" + suffix,
                "password",
                label + "_" + suffix + "@example.test",
                "Test Seller " + suffix
        );
    }

    private Admin admin(String label) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Admin admin = new Admin(
                "U-ADM-" + suffix.toUpperCase(),
                label + "_" + suffix,
                "password",
                label + "_" + suffix + "@example.test"
        );
        admin.setRole("ADMIN");
        admin.setFullName("Test Admin " + suffix);
        authenticationService.updateUser(admin);
        return admin;
    }

    private long countTransactions(String type) {
        return walletService.getTransactionsForAdmin(auditAdmin, "").stream()
                .filter(transaction -> type.equals(transaction.transactionType()))
                .count();
    }

    private long countTransactions(User user, String type) {
        return walletService.getTransactionsForAdmin(auditAdmin, user.getId()).stream()
                .filter(transaction -> type.equals(transaction.transactionType()))
                .count();
    }

    private double transactionTotal(String type) {
        return walletService.getTransactionsForAdmin(auditAdmin, "").stream()
                .filter(transaction -> type.equals(transaction.transactionType()))
                .mapToDouble(WalletTransaction::amount)
                .sum();
    }
}
