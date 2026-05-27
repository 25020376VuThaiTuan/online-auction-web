package org.example.server;

import org.example.auction.AuctionSettlement;
import org.example.auction.AuctionSettlementStatus;
import org.example.auction.AuctionStatus;
import org.example.auction.AuctionSummary;
import org.example.auction.UserNotification;
import org.example.model.Bid;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.model.ItemFactory;
import org.example.model.Seller;
import org.example.model.WalletAuthorization;
import org.example.model.WalletLinkedAccount;
import org.example.service.AuctionSettlementService;
import org.example.model.WalletRecoveryResult;
import org.example.model.WalletSummary;
import org.example.model.WalletTransaction;
import org.example.service.AuctionWorkflowService;
import org.example.service.AuthenticationService;
import org.example.service.MarketplaceDashboardService;
import org.example.service.WalletService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiPayloadFactoryTest {
    @Test
    void userPayloadIncludesBidderFinancialStateAndWallet() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "payload_" + suffix;
        Bidder bidder = (Bidder) AuthenticationService.getInstance().registerManualBidder(
                username,
                "secret",
                username + "@test.local",
                "Test Bidder " + suffix
        );
        bidder.setBalance(1_000.0);
        AuthenticationService.getInstance().updateUser(bidder);
        Item item = ItemFactory.createItem(
                "electronics",
                "ITEM-1",
                "Payload Test Item",
                "Test item",
                615.0,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(30),
                "Brand",
                12
        );
        AuctionSettlementService.getInstance().lockEntryDeposit(
                item,
                new AuctionSummary(
                        item.getId(),
                        item.getItemName(),
                        AuctionStatus.RUNNING,
                        615.0,
                        625.0,
                        1_800L,
                        0,
                        null
                ),
                bidder
        );
        WalletService.getInstance().setPin(bidder, "1234");

        ApiPayloadFactory payloadFactory = new ApiPayloadFactory(
                AuctionWorkflowService.getInstance(),
                MarketplaceDashboardService.getInstance()
        );

        Map<String, Object> payload = payloadFactory.user(bidder);

        assertEquals(bidder.getId(), payload.get("id"));
        assertEquals(1_000.0, (double) payload.get("balance"));
        assertEquals(125.0, (double) payload.get("lockedBalance"));
        assertEquals(875.0, (double) payload.get("availableBalance"));
        assertEquals(Map.of("ITEM-1", 125.0), payload.get("lockedDeposits"));

        Map<?, ?> wallet = (Map<?, ?>) payload.get("wallet");
        assertEquals(1_000.0, (double) wallet.get("balance"));
        assertEquals(125.0, (double) wallet.get("lockedBalance"));
        assertEquals(875.0, (double) wallet.get("availableBalance"));
        assertEquals(true, wallet.get("pinSet"));
        assertEquals(List.of(), wallet.get("linkedAccounts"));
        assertFalse(payload.containsKey("bankAccounts"));
    }

    @Test
    void recoveryPayloadNeverExposesRecoveryCode() {
        System.setProperty("auction.dev.exposeRecoveryCode", "true");
        ApiPayloadFactory payloadFactory = new ApiPayloadFactory(
                AuctionWorkflowService.getInstance(),
                MarketplaceDashboardService.getInstance()
        );

        try {
            Map<String, Object> payload = payloadFactory.walletRecovery(new WalletRecoveryResult(
                    true,
                    "Recovery email sent.",
                    "user@test.local"
            ));

            assertFalse(payload.containsKey("recoveryCode"));
        } finally {
            System.clearProperty("auction.dev.exposeRecoveryCode");
        }
    }

    @Test
    void notificationPayloadIncludesStablePopupKey() {
        UserNotification notification = new UserNotification(
                "USER-1",
                "AUCTION",
                "Auction finished",
                "A watched auction has finished.",
                LocalDateTime.of(2026, 5, 26, 12, 0)
        );
        ApiPayloadFactory payloadFactory = new ApiPayloadFactory(
                AuctionWorkflowService.getInstance(),
                MarketplaceDashboardService.getInstance()
        );

        Map<String, Object> payload = payloadFactory.notification(notification);

        assertEquals(notification.getPopupKey(), payload.get("popupKey"));
        assertEquals(notification.getDisplayText(), payload.get("displayText"));
    }

    @Test
    void walletAuthorizationAndTransactionPayloadsPreservePublicFields() {
        ApiPayloadFactory payloadFactory = new ApiPayloadFactory(
                AuctionWorkflowService.getInstance(),
                MarketplaceDashboardService.getInstance()
        );
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 27, 13, 0);
        WalletLinkedAccount account = new WalletLinkedAccount(
                "account-1",
                "user-1",
                "Primary Account",
                "Bank",
                "1234567890",
                1_200.0,
                true,
                createdAt
        );
        WalletTransaction transaction = new WalletTransaction(
                "tx-1",
                "user-1",
                "TOP_UP",
                300.0,
                100.0,
                400.0,
                "ref-1",
                "Top up",
                createdAt.plusMinutes(1)
        );
        WalletSummary summary = new WalletSummary(
                "user-1",
                400.0,
                50.0,
                350.0,
                true,
                List.of(account),
                List.of(transaction)
        );

        assertNull(payloadFactory.wallet(null));
        Map<String, Object> payload = payloadFactory.wallet(summary);

        assertEquals("user-1", payload.get("userId"));
        assertEquals(400.0, payload.get("balance"));
        assertEquals(true, payload.get("pinSet"));
        List<?> linkedAccounts = (List<?>) payload.get("linkedAccounts");
        Map<?, ?> linkedAccount = (Map<?, ?>) linkedAccounts.get(0);
        assertEquals("****7890", linkedAccount.get("maskedReference"));
        assertEquals("Bank - Primary Account (****7890)", linkedAccount.get("displayName"));
        List<?> transactions = (List<?>) payload.get("transactions");
        Map<?, ?> transactionPayload = (Map<?, ?>) transactions.get(0);
        assertEquals("TOP_UP", transactionPayload.get("transactionType"));
        assertEquals("2026-05-27T13:01:00", transactionPayload.get("createdAt"));

        assertNull(payloadFactory.walletAuthorization(null));
        Map<String, Object> authorization = payloadFactory.walletAuthorization(new WalletAuthorization(
                "wa_token",
                createdAt.plusHours(2)
        ));
        assertEquals("wa_token", authorization.get("token"));
        assertEquals("2026-05-27T15:00:00", authorization.get("expiresAt"));
    }

    @Test
    void settlementBidItemAndUserListPayloadsCoverNullAndRoleSpecificFields() {
        ApiPayloadFactory payloadFactory = new ApiPayloadFactory(
                AuctionWorkflowService.getInstance(),
                MarketplaceDashboardService.getInstance()
        );
        LocalDateTime finishedAt = LocalDateTime.of(2026, 5, 27, 14, 0);
        AuctionSettlement settlement = new AuctionSettlement(
                "item-1",
                "Payload item",
                "seller-1",
                "bidder-1",
                1_000.0,
                100.0,
                50.0,
                1_050.0,
                950.0,
                80.0,
                920.0,
                finishedAt
        );
        settlement.setStatus(AuctionSettlementStatus.AWAITING_BUYER_CONFIRMATION);
        settlement.setLockedRemainingPayment(950.0);
        settlement.setSellerReleasedAmount(920.0);
        settlement.setBuyerRefundedAmount(30.0);
        settlement.setBuyerConfirmationDeadline(finishedAt.plusDays(3));

        assertNull(payloadFactory.settlement(null));
        Map<String, Object> settlementPayload = payloadFactory.settlement(settlement);
        assertEquals("item-1", settlementPayload.get("itemId"));
        assertEquals("AWAITING_BUYER_CONFIRMATION", settlementPayload.get("status"));
        assertEquals(950.0, settlementPayload.get("lockedRemainingPayment"));
        assertEquals("2026-05-30T14:00:00", settlementPayload.get("buyerConfirmationDeadline"));
        assertTrue(((String) settlementPayload.get("displaySummary")).contains("Payload item"));

        Bid bid = new Bid("bid-1", "bidder-1", "item-1", 1_000.0, null);
        Map<String, Object> bidPayload = payloadFactory.bid(bid);
        assertEquals("bid-1", bidPayload.get("id"));
        assertNull(bidPayload.get("bidTime"));

        Item item = ItemFactory.createItem(
                "art",
                "item-payload",
                "Payload artwork",
                "No dates",
                10.0,
                null,
                null,
                "Artist",
                2026
        );
        item.setSellerId("seller-1");
        Map<String, Object> itemPayload = payloadFactory.item(item);
        assertEquals("item-payload", itemPayload.get("itemId"));
        assertNull(itemPayload.get("startTime"));
        assertEquals("N/A", itemPayload.get("displayEndTime"));

        Bidder bidder = new Bidder("bidder-2", "bidder2", "hash", "bidder2@test.local", 500.0);
        bidder.setRole("BIDDER");
        bidder.lockDeposit("item-payload", 125.0);
        Map<String, Object> bidderPayload = payloadFactory.userListItem(bidder);
        assertEquals(500.0, bidderPayload.get("balance"));
        assertEquals(125.0, bidderPayload.get("lockedBalance"));
        assertEquals(375.0, bidderPayload.get("availableBalance"));
        assertEquals(Map.of("item-payload", 125.0), bidderPayload.get("lockedDeposits"));

        Seller seller = new Seller("seller-2", "seller2", "hash", "seller2@test.local");
        seller.setRole("SELLER");
        Map<String, Object> sellerPayload = payloadFactory.userListItem(seller);
        assertEquals(0.0, sellerPayload.get("balance"));
        assertEquals(Map.of(), sellerPayload.get("lockedDeposits"));
    }

    @Test
    void auctionPayloadShouldReportMissingAuctionsAsNotFound() {
        ApiPayloadFactory payloadFactory = new ApiPayloadFactory(
                AuctionWorkflowService.getInstance(),
                MarketplaceDashboardService.getInstance()
        );

        assertThrows(
                ApiResourceNotFoundException.class,
                () -> payloadFactory.auction("missing-" + UUID.randomUUID())
        );
    }
}
