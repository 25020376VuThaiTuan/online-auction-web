package org.example.server;

import org.example.auction.AuctionStatus;
import org.example.auction.AuctionSummary;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.model.ItemFactory;
import org.example.service.AuctionSettlementService;
import org.example.model.WalletRecoveryResult;
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
    void recoveryPayloadDoesNotExposeCodeByDefault() {
        System.clearProperty("auction.dev.exposeRecoveryCode");
        ApiPayloadFactory payloadFactory = new ApiPayloadFactory(
                AuctionWorkflowService.getInstance(),
                MarketplaceDashboardService.getInstance()
        );

        Map<String, Object> payload = payloadFactory.walletRecovery(new WalletRecoveryResult(
                true,
                "Recovery email sent.",
                "user@test.local",
                "123456"
        ));

        assertFalse(payload.containsKey("recoveryCode"));
    }
}
