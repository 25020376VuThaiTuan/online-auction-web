package org.example.server;

import com.sun.net.httpserver.HttpServer;
import org.example.auction.BidValidationResult;
import org.example.client.AuctionApiClient;
import org.example.model.ApprovalStatus;
import org.example.model.Item;
import org.example.model.User;
import org.example.model.WalletLinkedAccount;
import org.example.model.WalletSummary;
import org.example.service.AuthenticationService;
import org.example.service.AuctionWorkflowService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiRoleWorkflowIntegrationTest {
    private static final String PASSWORD = "secure123";
    private static final String PIN = "2468";

    private HttpServer server;
    private String previousBaseUrl;
    private AuctionApiClient bidderClient;
    private AuctionApiClient sellerClient;
    private AuctionApiClient adminClient;

    @BeforeEach
    void startServerAndClients() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", new AuctionApiHandler(
                AuthenticationService.getInstance(),
                AuctionWorkflowService.getInstance(),
                new ApiSessionService(),
                new AuctionRealtimeBroker()
        ));
        server.start();

        previousBaseUrl = System.getProperty("auction.api.baseUrl");
        System.setProperty("auction.api.baseUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/api");
        bidderClient = newApiClient();
        sellerClient = newApiClient();
        adminClient = newApiClient();
    }

    @AfterEach
    void stopServerAndRestoreProperty() {
        if (previousBaseUrl == null) {
            System.clearProperty("auction.api.baseUrl");
        } else {
            System.setProperty("auction.api.baseUrl", previousBaseUrl);
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void bidderSellerAndAdminClientsCompleteSettlementAndSellerWalletReceivesPayout() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String bidderFullName = "Role Bidder " + suffix;
        String sellerFullName = "Role Seller " + suffix;

        AuctionApiClient.AuthResult bidder = bidderClient.registerManualBidder(
                "role_bidder_" + suffix,
                PASSWORD,
                "role_bidder_" + suffix + "@test.local",
                bidderFullName
        );
        AuctionApiClient.AuthResult seller = sellerClient.registerManualSeller(
                "role_seller_" + suffix,
                PASSWORD,
                "role_seller_" + suffix + "@test.local",
                sellerFullName
        );
        User adminSeed = AuthenticationService.getInstance().registerManualBidder(
                "role_admin_" + suffix,
                PASSWORD,
                "role_admin_" + suffix + "@test.local",
                "Role Admin " + suffix
        );
        AuthenticationService.getInstance().updateUserRole(adminSeed.getId(), "ADMIN");
        AuctionApiClient.AuthResult admin = adminClient.login("role_admin_" + suffix, PASSWORD);

        assertEquals("BIDDER", bidder.user().getRole());
        assertEquals("SELLER", seller.user().getRole());
        assertEquals("ADMIN", admin.user().getRole());

        fundBidderWallet(bidder.token(), bidderFullName);
        sellerClient.setWalletPin(seller.token(), PIN);

        Item item = sellerClient.addSellerItem(
                seller.token(),
                "electronics",
                "Role Workflow " + suffix,
                "Integration role workflow item",
                100.0,
                LocalDateTime.now().minusMinutes(2),
                LocalDateTime.now().plusMinutes(20),
                "Integration",
                12
        );
        assertNotNull(item);

        Item approved = adminClient.updateItemApproval(admin.token(), item.getId(), ApprovalStatus.APPROVED);
        assertEquals(ApprovalStatus.APPROVED, approved.getApprovalStatus());
        sellerClient.startAuction(seller.token(), item.getId());

        assertTrue(bidderClient.confirmAuctionEntry(bidder.token(), item.getId(), PIN).result().accepted());
        BidValidationResult bid = bidderClient.placeBid(bidder.token(), item.getId(), 150.0, PIN);
        assertTrue(bid.accepted(), bid.message());

        sellerClient.finishAuction(seller.token(), item.getId());
        AuctionApiClient.SettlementDetail settlement = bidderClient.getSettlement(bidder.token(), item.getId());
        assertNotNull(settlement);
        assertEquals(150.0, settlement.totalBuyerDue(), 0.001);

        bidderClient.admitWinnerResult(bidder.token(), item.getId(), PIN);
        sellerClient.markGoodsShipped(seller.token(), item.getId(), PIN);
        AuctionApiClient.SettlementDetail paid = bidderClient.confirmGoodsReceived(bidder.token(), item.getId(), PIN);

        assertEquals("PAYMENT_RELEASED", paid.status());
        AuctionApiClient.CurrentUserSnapshot sellerSnapshot = sellerClient.getCurrentUserSnapshot(seller.token());
        assertNotNull(sellerSnapshot.wallet());
        assertEquals(paid.sellerPayout(), sellerSnapshot.wallet().balance(), 0.001);
        assertEquals(paid.sellerPayout(), paid.sellerReleasedAmount(), 0.001);
    }

    private void fundBidderWallet(String token, String accountHolderName) {
        bidderClient.setWalletPin(token, PIN);
        WalletSummary wallet = bidderClient.addWalletAccount(
                token,
                accountHolderName,
                "Integration Bank",
                "ROLE-" + UUID.randomUUID().toString().substring(0, 8),
                500.0,
                true,
                PIN
        );
        WalletLinkedAccount account = wallet.linkedAccounts().getFirst();
        bidderClient.receiveWalletMoney(token, account.id(), 300.0, PIN);
    }

    private AuctionApiClient newApiClient() throws Exception {
        Constructor<AuctionApiClient> constructor = AuctionApiClient.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
