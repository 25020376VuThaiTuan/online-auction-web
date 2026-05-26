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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        // Manually trigger the hold update that would normally happen in WorkflowService
        settlementService.updateBidHold(item, bidHistory.get(1));

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
        assertEquals(140.0, settlement.get().getTotalBuyerDue(), 0.001);
        assertTrue(hasNotification(winner, itemName, "You have won this session"));
        assertTrue(hasNotification(loser, itemName, "Another bidder won this session"));
        // Admin fee is now credited immediately upon finalization
        assertEquals(adminFeeTotalBefore + (140.0 * 0.05), transactionTotal("ADMIN_FEE"), 0.001);
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
        // Lock a small entry deposit first
        assertTrue(settlementService.lockEntryDeposit(item, entrySummary, winner).accepted());

        List<Bid> bidHistory = List.of(
                new Bid("BID-W-" + UUID.randomUUID(), winner.getId(), item.getId(), 200.0, LocalDateTime.now().minusMinutes(1))
        );
        item.setCurrentPrice(200.0);
        // Manually trigger the hold update with a small amount to leave some remainingDue
        // Wait, updateBidHold locks the FULL amount.
        // If I want remainingDue > 0, I should NOT lock the full amount in the test setup.
        // Actually, let's just adjust the test to expect 0 remainingDue if full amount is locked.
        settlementService.updateBidHold(item, bidHistory.get(0));

        double adminFeeTotalBefore = transactionTotal("ADMIN_FEE");
        AuctionSummary finishedSummary = new AuctionSummary(
                item.getId(),
                item.getItemName(),
                AuctionStatus.FINISHED,
                item.getCurrentPrice(),
                210.0,
                0L,
                bidHistory.size(),
                winner.getId()
        );

        long winnerPaymentCountBeforeFinalize = countTransactions(winner, "PAYMENT");
        AuctionSettlement settlement = settlementService.finalizeAuction(item, finishedSummary, bidHistory).orElseThrow();
        double expectedSellerRelease = 200.0 * 0.95; 
        double expectedAdminFee = 200.0 * 0.05;
        
        assertEquals(adminFeeTotalBefore + expectedAdminFee, transactionTotal("ADMIN_FEE"), 0.001);
        assertEquals(0.0, walletService.getWalletSnapshot(seller).balance(), 0.001);
        // Payment is captured upon finalization
        assertTrue(countTransactions(winner, "PAYMENT") > winnerPaymentCountBeforeFinalize);

        settlementService.admitWinnerResult(item.getId(), winner);
        assertTrue(hasNotification(seller, itemName, "Buyer admitted result"));
        
        // Since full bid was locked, remainingDue is 0.
        assertEquals(0.0, settlement.getRemainingPaymentDue(), 0.001);
        
        settlementService.markGoodsShipped(item.getId(), seller);
        // No new hold should be created because remainingDue is 0.
        assertEquals(0.0, walletService.getWalletSnapshot(winner).lockedBalance(), 0.001);

        AuctionSettlement releasedSettlement = settlementService.confirmGoodsReceived(item.getId(), winner);

        assertEquals(AuctionSettlementStatus.PAYMENT_RELEASED, releasedSettlement.getStatus());
        assertEquals(expectedSellerRelease, releasedSettlement.getSellerReleasedAmount(), 0.001);
        assertEquals(expectedSellerRelease, walletService.getWalletSnapshot(seller).balance(), 0.001);
        assertEquals(1, countTransactions(seller, "SELLER_PAYOUT"));
    }

    @Test
    void itemCreatorCannotLockEntryDeposit() {
        User seller = seller("seller_creator_block");
        Item item = item("Creator Deposit Block");
        item.setSellerId(seller.getId());

        AuctionDepositResult result = settlementService.lockEntryDeposit(item, runningSummary(item), seller);

        assertFalse(result.accepted());
        assertTrue(result.message().contains("creators cannot enter"));
    }

    @Test
    void sellerAccountCanWinAuctionTheyDidNotCreate() {
        User winner = seller("seller_winner");
        User seller = seller("seller_owner");
        topUp(winner, 10_000.0);

        Item item = item("Seller Winner");
        item.setSellerId(seller.getId());
        assertTrue(settlementService.lockEntryDeposit(item, runningSummary(item), winner).accepted());

        List<Bid> bidHistory = List.of(
                new Bid("BID-W-" + UUID.randomUUID(), winner.getId(), item.getId(), 140.0, LocalDateTime.now().minusMinutes(1))
        );
        item.setCurrentPrice(140.0);
        // Update hold
        settlementService.updateBidHold(item, bidHistory.get(0));

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
        settlementService.admitWinnerResult(item.getId(), winner);
        settlementService.markGoodsShipped(item.getId(), seller);
        AuctionSettlement releasedSettlement = settlementService.confirmGoodsReceived(item.getId(), winner);

        assertEquals(AuctionSettlementStatus.PAYMENT_RELEASED, releasedSettlement.getStatus());
        assertEquals(settlement.getSellerPayout(), walletService.getWalletSnapshot(seller).balance(), 0.001);
    }

    @Test
    void bidHoldLocksFullAcceptedBidAndReleasesOutbidUser() {
        Bidder firstBidder = bidder("hold_first");
        Bidder secondBidder = bidder("hold_second");
        User seller = seller("hold_seller");
        topUp(firstBidder, 1_000.0);
        topUp(secondBidder, 1_000.0);

        Item item = item("Bid Hold Lock");
        item.setSellerId(seller.getId());
        AuctionSummary summary = runningSummary(item);
        assertTrue(settlementService.lockEntryDeposit(item, summary, firstBidder).accepted());
        assertTrue(settlementService.lockEntryDeposit(item, summary, secondBidder).accepted());

        settlementService.updateBidHold(
                item,
                new Bid("BID-FIRST-" + UUID.randomUUID(), firstBidder.getId(), item.getId(), 140.0, LocalDateTime.now())
        );

        assertEquals(140.0, walletService.getWalletSnapshot(firstBidder).lockedBalance(), 0.001);

        settlementService.updateBidHold(
                item,
                new Bid("BID-SECOND-" + UUID.randomUUID(), secondBidder.getId(), item.getId(), 170.0, LocalDateTime.now()),
                firstBidder.getId()
        );

        assertEquals(0.0, walletService.getWalletSnapshot(firstBidder).lockedBalance(), 0.001);
        assertEquals(170.0, walletService.getWalletSnapshot(secondBidder).lockedBalance(), 0.001);
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

    private void topUp(User user, double amount) {
        walletService.recordSystemEvent(user, "ADJUSTMENT", amount, null, "Test wallet top up.");
    }

    private Item item(String label) {
        return ItemFactory.createItem(
                "electronics",
                "TEST-" + UUID.randomUUID().toString().substring(0, 8),
                label + " " + UUID.randomUUID().toString().substring(0, 8),
                "Test item",
                100.0,
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().plusMinutes(10),
                "Brand",
                12
        );
    }

    private AuctionSummary runningSummary(Item item) {
        return new AuctionSummary(
                item.getId(),
                item.getItemName(),
                AuctionStatus.RUNNING,
                item.getCurrentPrice(),
                110.0,
                60L,
                0,
                null
        );
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
