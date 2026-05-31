package org.example.controller;

import javafx.collections.FXCollections;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import com.sun.net.httpserver.HttpServer;
import org.example.auction.AuctionSettlement;
import org.example.auction.AuctionSettlementStatus;
import org.example.client.AuctionApiClient;
import org.example.model.Admin;
import org.example.model.ApprovalStatus;
import org.example.model.Bid;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.model.ItemFactory;
import org.example.model.Seller;
import org.example.model.User;
import org.example.model.WalletLinkedAccount;
import org.example.model.WalletSummary;
import org.example.model.WalletTransaction;
import org.example.server.ApiSessionService;
import org.example.server.AuctionApiHandler;
import org.example.server.AuctionRealtimeBroker;
import org.example.service.AuthenticationService;
import org.example.service.AuctionWorkflowService;
import org.example.service.MarketplaceDashboardService;
import org.example.service.WalletService;
import org.example.state.ApplicationSession;
import org.example.util.AuctionCatalogFilters;
import org.example.util.CredentialHasher;
import org.example.viewmodel.AuctionEligibilityEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardControllerCoverageExpansionTest {
    private static final String PIN = "2468";

    private final ApplicationSession session = ApplicationSession.getInstance();
    private final MarketplaceDashboardService dashboardService = MarketplaceDashboardService.getInstance();

    @BeforeAll
    static void initToolkit() {
        JavaFxTestSupport.startToolkit();
    }

    @AfterEach
    void clearSession() {
        JavaFxTestSupport.closeOpenDialogs();
        session.clearWatchedAuctions();
        session.logout();
    }

    @Test
    void dashboardConfigurationWalletSummaryAndAuctionFiltersUpdateRealControls() throws Exception {
        Bidder bidder = bidder();
        session.login(bidder);
        DashboardController controller = dashboardController();

        invoke(controller, "configureTables");
        invoke(controller, "configureRoleTabs");
        invoke(controller, "bindCurrentUserFields");

        assertTrue(field(controller, "sellerTab", Tab.class).isDisabled());
        assertTrue(field(controller, "adminTab", Tab.class).isDisabled());
        assertEquals("Dashboard Bidder", field(controller, "fullNameField", TextField.class).getText());
        assertEquals("555-0100", field(controller, "phoneField", TextField.class).getText());
        assertEquals("12 Test Street", field(controller, "addressArea", TextArea.class).getText());
        assertEquals("https://example.test/avatar.png", field(controller, "avatarUrlField", TextField.class).getText());
        assertEquals(List.of("BIDDER", "SELLER", "ADMIN"), field(controller, "roleChoiceBox", ChoiceBox.class).getItems());
        assertEquals("electronics", field(controller, "sellerItemTypeChoiceBox", ChoiceBox.class).getValue());

        WalletLinkedAccount primary = walletAccount("ACCOUNT-1", bidder.getId(), true, 700.0);
        WalletLinkedAccount secondary = walletAccount("ACCOUNT-2", bidder.getId(), false, 150.0);
        WalletTransaction transaction = walletTransaction("TX-1", bidder.getId(), 100.0, 900.0);
        WalletSummary wallet = new WalletSummary(
                bidder.getId(),
                950.0,
                150.0,
                800.0,
                true,
                List.of(primary, secondary),
                List.of(transaction)
        );
        setField(controller, "openedWalletSummary", wallet);
        invoke(controller, "refreshWallet", wallet);

        assertEquals("$950.00", field(controller, "balanceLabel", Label.class).getText());
        assertEquals("$150.00", field(controller, "lockedBalanceLabel", Label.class).getText());
        assertEquals("$800.00", field(controller, "availableBalanceLabel", Label.class).getText());
        assertEquals("$950.00", field(controller, "walletBalanceLabel", Label.class).getText());
        assertEquals("Set", field(controller, "walletPinStatusLabel", Label.class).getText());
        assertTrue(field(controller, "setWalletPinButton", Button.class).isDisabled());
        assertEquals(950.0, bidder.getBalance());
        assertEquals(List.of(transaction), field(controller, "walletTransactionTable", TableView.class).getItems());
        assertEquals(List.of(primary, secondary), field(controller, "walletAccountTable", TableView.class).getItems());
        assertEquals("ACCOUNT-1", invoke(controller, "selectedWalletAccountId"));
        field(controller, "walletAccountTable", TableView.class).getSelectionModel().select(secondary);
        assertEquals("ACCOUNT-2", invoke(controller, "selectedWalletAccountId"));
        WalletSummary financialRefresh = new WalletSummary(
                bidder.getId(),
                975.0,
                25.0,
                950.0,
                true,
                List.of(),
                List.of()
        );
        bidder.setBalance(975.0);
        invoke(controller, "refreshWalletSnapshot", bidder, financialRefresh);
        assertEquals("$975.00", field(controller, "walletBalanceLabel", Label.class).getText());
        assertEquals(975.0, bidder.getBalance(), 0.001);
        invoke(controller, "refreshWalletSnapshot", bidder);
        assertEquals("$975.00", field(controller, "walletBalanceLabel", Label.class).getText());

        List<AuctionEligibilityEntry> entries = List.of(
                auctionEntry("A-1", "Camera Kit", "RUNNING", 200.0, 210.0, true, true, 60L),
                auctionEntry("A-2", "Desk Lamp", "OPEN", 50.0, 60.0, true, false, 120L),
                auctionEntry("A-3", "Signed Print", "FINISHED", 80.0, 90.0, false, false, 0L)
        );
        invoke(controller, "applyMarketplaceSummary", entries);
        assertEquals("3", field(controller, "overviewAuctionCountLabel", Label.class).getText());
        assertEquals("2", field(controller, "overviewRunningCountLabel", Label.class).getText());
        assertEquals("1", field(controller, "overviewEnteredCountLabel", Label.class).getText());
        assertEquals("2", field(controller, "overviewEligibleCountLabel", Label.class).getText());

        invoke(controller, "applyAuctionEntries", entries);
        assertEquals(3, field(controller, "auctionTable", TableView.class).getItems().size());
        assertEquals("3 auction sessions available", field(controller, "auctionResultsSummaryLabel", Label.class).getText());
        assertEquals("Select an auction from Auction List", field(controller, "selectedAuctionLabel", Label.class).getText());
        assertTrue(field(controller, "placeDashboardBidButton", Button.class).isDisabled());

        field(controller, "dashboardAuctionSearchField", TextField.class).setText("camera");
        assertEquals(1, field(controller, "auctionTable", TableView.class).getItems().size());
        assertEquals("Showing 1 of 3 auction sessions", field(controller, "auctionResultsSummaryLabel", Label.class).getText());

        field(controller, "dashboardAuctionSearchField", TextField.class).clear();
        field(controller, "dashboardEligibleOnlyCheckBox", CheckBox.class).setSelected(true);
        assertEquals(2, field(controller, "auctionTable", TableView.class).getItems().size());

        session.watchAuction("A-2");
        field(controller, "dashboardEligibleOnlyCheckBox", CheckBox.class).setSelected(false);
        field(controller, "dashboardWatchedOnlyCheckBox", CheckBox.class).setSelected(true);
        assertEquals(List.of("A-2"), field(controller, "auctionTable", TableView.class).getItems().stream()
                .map(entry -> ((AuctionEligibilityEntry) entry).getItemId())
                .toList());

        invoke(controller, "handleWatchVisibleAuctions");
        assertTrue(session.isAuctionWatched("A-2"));
        invoke(controller, "handleClearWatchedAuctions");
        assertFalse(session.isAuctionWatched("A-2"));
        assertEquals("No watched auction sessions match the current filters.", field(controller, "auctionResultsSummaryLabel", Label.class).getText());

        invoke(controller, "handleClearAuctionFilters");
        assertFalse(field(controller, "dashboardWatchedOnlyCheckBox", CheckBox.class).isSelected());
        assertEquals(3, field(controller, "auctionTable", TableView.class).getItems().size());
    }

    @Test
    void initializationProfileAvatarRecoveryAndBidIncrementHandlersUseLocalBranches() throws Exception {
        String previousBaseUrl = System.getProperty("auction.api.baseUrl");
        try {
            System.setProperty("auction.api.baseUrl", "");
            Bidder bidder = registeredBidder("dashboard-init");
            dashboardService.setWalletPin(bidder, PIN);
            session.login(bidder, "api-token");
            DashboardController controller = dashboardController();
            setField(controller, "apiClient", newApiClient());

            JavaFxTestSupport.runAndWait(() -> {
                try {
                    controller.initialize();
                    invoke(controller, "stopRefreshLoop");
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            });

            assertEquals("api-token", invoke(controller, "apiToken"));
            assertTrue(invokeOnFx(controller, "walletAuthorizationCredential", PIN).toString().startsWith("wa_"));

            invokeWithClosedDialog(controller, "handleTestConnection");
            JavaFxTestSupport.closeNextDialog(ButtonType.OK);
            invokeOnFx(controller, "showConnectionSuccess",
                    new AuctionApiClient.ConnectionTestResult("http://127.0.0.1/api", "ok", "2026-05-27T12:00:00"));
            JavaFxTestSupport.closeNextDialog(ButtonType.OK);
            invokeOnFx(controller, "showConnectionFailure",
                    new AuctionApiClient.ApiClientException("Could not reach API", new IOException("down")));
            JavaFxTestSupport.closeNextDialog(ButtonType.OK);
            invokeOnFx(controller, "showConnectionFailure", new IllegalStateException("bad status"));

            field(controller, "fullNameField", TextField.class).setText("Dashboard Init Updated");
            field(controller, "phoneField", TextField.class).setText("555-0200");
            field(controller, "addressArea", TextArea.class).setText("Updated Avenue");
            invokeWithClosedDialog(controller, "handleSaveProfile");
            assertEquals("Dashboard Init Updated", bidder.getFullName());

            field(controller, "avatarUrlField", TextField.class).setText("https://example.test/init-avatar.png");
            invokeWithClosedDialog(controller, "handleSaveAvatar");
            assertEquals("https://example.test/init-avatar.png", bidder.getAvatarUrl());

            invokeWithClosedDialog(controller, "handleRequestWalletPinRecovery");
            field(controller, "recoveryCodeField", TextField.class).clear();
            field(controller, "newWalletPinField", PasswordField.class).clear();
            invokeWithClosedDialog(controller, "handleResetWalletPin");
            @SuppressWarnings("unchecked")
            java.util.Map<String, String> recoveryCodes = (java.util.Map<String, String>) field(
                    WalletService.getInstance(),
                    "recoveryCodesByUserId"
            );
            recoveryCodes.put(bidder.getId(), "999999");
            field(controller, "recoveryCodeField", TextField.class).setText("999999");
            field(controller, "newWalletPinField", PasswordField.class).setText("1357");
            invokeWithClosedDialog(controller, "handleResetWalletPin");
            assertEquals("", field(controller, "recoveryCodeField", TextField.class).getText());

            AuctionEligibilityEntry entry = auctionEntry("INIT-AUCTION", "Init Camera", "RUNNING", 100.0, 110.0, true, true, 120L);
            TableView<AuctionEligibilityEntry> table = field(controller, "auctionTable");
            setField(controller, "suppressAuctionSelectionRefresh", true);
            table.setItems(FXCollections.observableArrayList(entry));
            table.getSelectionModel().select(entry);
            setField(controller, "suppressAuctionSelectionRefresh", false);
            setField(controller, "selectedAuctionId", entry.getItemId());
            field(controller, "bidAmountField", TextField.class).setText("110.00");

            invokeOnFx(controller, "handleAddTenToBid");
            assertEquals("120.00", field(controller, "bidAmountField", TextField.class).getText());
            invokeOnFx(controller, "handleAddFiftyToBid");
            assertEquals("170.00", field(controller, "bidAmountField", TextField.class).getText());
            invokeOnFx(controller, "handleAddHundredToBid");
            assertEquals("270.00", field(controller, "bidAmountField", TextField.class).getText());

            JavaFxTestSupport.closeNextDialog(ButtonType.OK);
            invokeOnFx(controller, "runBuyerSettlementAction", "No item", "", (Runnable) () -> {
                throw new AssertionError("Action should not run without an item id.");
            });
        } finally {
            restoreProperty("auction.api.baseUrl", previousBaseUrl);
        }
    }

    @Test
    void auctionSelectionBidPanelAndFormattingHelpersCoverDashboardBranches() throws Exception {
        Bidder bidder = bidder();
        session.login(bidder);
        DashboardController controller = dashboardController();
        AuctionEligibilityEntry runningEntry = auctionEntry("A-9", "Running Camera", "RUNNING", 140.0, 150.0, true, true, 90L);
        TableView<AuctionEligibilityEntry> auctionTable = field(controller, "auctionTable");
        auctionTable.setItems(FXCollections.observableArrayList(runningEntry));
        auctionTable.getSelectionModel().select(runningEntry);
        setField(controller, "selectedAuctionId", "A-9");

        invoke(controller, "showSelectedAuctionSummary", runningEntry, true);
        assertEquals("Running Camera [RUNNING]", field(controller, "selectedAuctionLabel", Label.class).getText());
        assertEquals("150.00", field(controller, "bidAmountField", TextField.class).getText());
        assertFalse(field(controller, "bidAmountField", TextField.class).isDisabled());
        assertFalse(field(controller, "placeDashboardBidButton", Button.class).isDisabled());
        assertTrue(field(controller, "confirmAuctionEntryButton", Button.class).isDisabled());
        assertTrue(field(controller, "admitDashboardResultButton", Button.class).isDisabled());

        invoke(controller, "applyBidIncrement", 10.0);
        assertEquals("160.00", field(controller, "bidAmountField", TextField.class).getText());
        field(controller, "bidAmountField", TextField.class).setText("not numeric");
        invoke(controller, "applyBidIncrement", 50.0);
        assertEquals("200.00", field(controller, "bidAmountField", TextField.class).getText());
        invoke(controller, "readyBidAmountInput", 190.0, false);
        assertEquals("200.00", field(controller, "bidAmountField", TextField.class).getText());
        invoke(controller, "readyBidAmountInput", 190.0, true);
        assertEquals("190.00", field(controller, "bidAmountField", TextField.class).getText());
        assertSame(runningEntry, invoke(controller, "selectedAuctionEntry"));
        auctionTable.getSelectionModel().clearSelection();
        assertSame(runningEntry, invoke(controller, "selectedAuctionEntry"));
        setField(controller, "selectedAuctionId", "missing");
        assertNull(invoke(controller, "selectedAuctionEntry"));

        LocalDateTime firstBidTime = LocalDateTime.of(2026, 5, 27, 10, 0);
        List<Bid> bidHistory = List.of(
                new Bid("BID-1", bidder.getId(), "A-9", 170.0, firstBidTime),
                new Bid("BID-2", "BIDDER-OTHER", "A-9", 180.0, firstBidTime.plusMinutes(1))
        );
        invoke(controller, "applyBidHistory", bidHistory);
        assertTrue(field(controller, "currentWinnerLabel", Label.class).getText().contains("BIDDER-OTHER"));
        assertEquals(2, field(controller, "bidNotificationList", ListView.class).getItems().size());
        assertEquals(1, field(controller, "bidHistoryChart", LineChart.class).getData().size());

        invoke(controller, "addBidActivityNotification", "Dashboard Bidder", 190.0, firstBidTime.plusMinutes(2), "accepted");
        assertTrue(field(controller, "bidNotificationList", ListView.class).getItems().getFirst().toString().contains("Dashboard Bidder"));
        invoke(controller, "addBidPanelMessage", "Auto-bid enabled");
        assertTrue(field(controller, "bidNotificationList", ListView.class).getItems().getFirst().toString().contains("Auto-bid enabled"));
        invoke(controller, "clearBidStatusViews");
        assertEquals("Current winner: N/A", field(controller, "currentWinnerLabel", Label.class).getText());
        assertTrue(field(controller, "bidNotificationList", ListView.class).getItems().isEmpty());

        Object notification = newNested(
                "org.example.controller.DashboardController$DashboardNotification",
                new Class<?>[]{String.class, String.class, String.class, String.class},
                "",
                "Payment completed",
                "Funds released",
                ""
        );
        invoke(controller, "applyNotifications", List.of(notification));
        assertEquals(List.of("Payment completed: Funds released"), field(controller, "notificationList", ListView.class).getItems());
        assertTrue((boolean) invoke(controller, "isPopupEligibleNotification", notification));

        Object quietNotification = newNested(
                "org.example.controller.DashboardController$DashboardNotification",
                new Class<?>[]{String.class, String.class, String.class, String.class},
                "key",
                "Profile saved",
                "Done",
                "Profile saved"
        );
        assertFalse((boolean) invoke(controller, "isPopupEligibleNotification", quietNotification));

        assertEquals("eligible-ready", invoke(controller, "eligibleStyleClass", "Can Enter"));
        assertEquals("", invoke(controller, "value", (Object) null));
        assertEquals(1234.5, (double) invoke(controller, "parseAmount", "$1,234.50"));
        assertEquals(0.0, (double) invoke(controller, "parseOptionalAmount", ""));
        assertEquals("12.35", invoke(controller, "formatAmountInput", 12.345));
        assertEquals("N/A", invoke(controller, "formatDateTime", (Object) null));
        assertTrue(invoke(controller, "dashboardAlertKey", "scope", "message").toString().contains(bidder.getId()));
        assertEquals("deep", invoke(controller, "refreshFailureMessage", new CompletionException(new IllegalStateException("deep"))));
        assertEquals("fallback", invoke(controller, "refreshFailureMessage", new ExecutionException(new IllegalArgumentException(" ")), "fallback"));
        assertEquals("root", ((Throwable) invoke(controller, "unwrapCompletionException",
                new CompletionException(new CompletionException(new IllegalArgumentException("root"))))).getMessage());
        invoke(controller, "refreshViewAsync", false);
    }

    @Test
    void confirmedAuctionTableOpenUsesLockedDepositBeforeRefreshRebuildsEntry() throws Exception {
        Bidder bidder = bidder();
        bidder.lockDeposit("A-10", 25.0);
        session.login(bidder);
        DashboardController controller = dashboardController();
        Tab auctionListTab = new Tab("Auction List");
        Tab biddingTab = new Tab("Bidding");
        setField(controller, "dashboardTabPane", new TabPane(auctionListTab, biddingTab));
        AuctionEligibilityEntry staleEntry = auctionEntry("A-10", "Confirmed Camera", "RUNNING", 100.0, 110.0, true, false, 90L);

        field(controller, "auctionTable", TableView.class).setItems(FXCollections.observableArrayList(staleEntry));
        field(controller, "auctionTable", TableView.class).getSelectionModel().select(staleEntry);

        invoke(controller, "openAuctionBiddingFromTable", staleEntry);

        assertEquals("A-10", field(controller, "selectedAuctionId"));
        assertEquals("A-10", session.getSelectedAuctionId().orElseThrow());
        assertSame(biddingTab, field(controller, "dashboardTabPane", TabPane.class).getSelectionModel().getSelectedItem());
        assertTrue(field(controller, "confirmAuctionEntryButton", Button.class).isDisabled());
        assertFalse(field(controller, "placeDashboardBidButton", Button.class).isDisabled());
        assertTrue(field(controller, "selectedAuctionDepositLabel", Label.class).getText().contains("Entered"));
    }

    @Test
    void dashboardNotificationPopupsDeduplicateOnlyWithinActiveSession() throws Exception {
        Bidder bidder = bidder();
        session.login(bidder);
        DashboardController controller = dashboardController();
        String key = "payment-completed-" + UUID.randomUUID();
        Object notification = newNested(
                "org.example.controller.DashboardController$DashboardNotification",
                new Class<?>[]{String.class, String.class, String.class, String.class},
                key,
                "Payment completed",
                "Funds released",
                "Payment completed: Funds released"
        );

        CompletableFuture<Boolean> firstDialog = JavaFxTestSupport.closeNextDialogAndTrack(
                ButtonType.OK,
                TimeUnit.SECONDS.toMillis(5)
        );
        invokeOnFx(controller, "showNotificationPopups", List.of(notification));
        assertTrue(firstDialog.get(6, TimeUnit.SECONDS));
        assertFalse(session.rememberNotificationPopup(key));

        CompletableFuture<Boolean> duplicateDialog = JavaFxTestSupport.closeNextDialogAndTrack(ButtonType.OK, 300);
        invokeOnFx(controller, "showNotificationPopups", List.of(notification));
        assertFalse(duplicateDialog.get(1, TimeUnit.SECONDS));

        session.logout();
        session.login(bidder);
        CompletableFuture<Boolean> nextSessionDialog = JavaFxTestSupport.closeNextDialogAndTrack(
                ButtonType.OK,
                TimeUnit.SECONDS.toMillis(5)
        );
        invokeOnFx(controller, "showNotificationPopups", List.of(notification));
        assertTrue(nextSessionDialog.get(6, TimeUnit.SECONDS));
        assertFalse(session.rememberNotificationPopup(key));
    }

    @Test
    void sellerAndAdminSectionsApplyRoleSpecificStateAndSnapshots() throws Exception {
        Seller seller = seller();
        session.login(seller);
        DashboardController sellerController = dashboardController();
        invoke(sellerController, "configureTables");
        invoke(sellerController, "configureRoleTabs");

        assertFalse(field(sellerController, "sellerTab", Tab.class).isDisabled());
        assertTrue(field(sellerController, "adminTab", Tab.class).isDisabled());
        invoke(sellerController, "refreshAccountSummary", seller, null);
        assertEquals("$0.00", field(sellerController, "balanceLabel", Label.class).getText());

        Item ownItem = item("SELLER-ITEM-1", seller.getId());
        Item otherItem = item("SELLER-ITEM-2", "OTHER-SELLER");
        invoke(sellerController, "applySellerItems", List.of(ownItem), seller);
        assertEquals(1, field(sellerController, "sellerItemsTable", TableView.class).getItems().size());
        assertTrue(field(sellerController, "sellerStartAuctionButton", Button.class).isDisabled());

        invoke(sellerController, "applySellerSelectionState", ownItem, true);
        assertFalse(field(sellerController, "sellerStartAuctionButton", Button.class).isDisabled());
        assertFalse(field(sellerController, "sellerFinishAuctionButton", Button.class).isDisabled());
        assertFalse(field(sellerController, "sellerMarkShippedButton", Button.class).isDisabled());
        invoke(sellerController, "applySellerSelectionState", otherItem, true);
        assertTrue(field(sellerController, "sellerStartAuctionButton", Button.class).isDisabled());
        assertTrue(field(sellerController, "sellerMarkShippedButton", Button.class).isDisabled());

        TableView<Item> sellerItemsTable = field(sellerController, "sellerItemsTable");
        setField(sellerController, "suppressSellerSelectionRefresh", true);
        sellerItemsTable.setItems(FXCollections.observableArrayList(ownItem));
        sellerItemsTable.getSelectionModel().select(ownItem);
        setField(sellerController, "suppressSellerSelectionRefresh", false);
        Object sellerSnapshot = newNested(
                "org.example.controller.DashboardController$SellerSelectionDetailSnapshot",
                new Class<?>[]{long.class, String.class, List.class, boolean.class},
                1L,
                ownItem.getId(),
                List.of("10:00 - Dashboard Bidder - $100.00"),
                true
        );
        invoke(sellerController, "applySellerSelectionDetailSnapshot", sellerSnapshot);
        assertEquals(List.of("10:00 - Dashboard Bidder - $100.00"), field(sellerController, "sellerBidHistoryList", ListView.class).getItems());
        assertFalse(field(sellerController, "sellerMarkShippedButton", Button.class).isDisabled());
        assertFalse((boolean) invoke(sellerController, "loadSellerCanShip", false, null, ""));

        session.logout();
        Admin admin = admin();
        session.login(admin);
        DashboardController adminController = dashboardController();
        invoke(adminController, "configureTables");
        invoke(adminController, "configureRoleTabs");
        assertFalse(field(adminController, "sellerTab", Tab.class).isDisabled());
        assertFalse(field(adminController, "adminTab", Tab.class).isDisabled());

        AuctionSettlement settlement = settlement();
        Object success = invokeStatic(
                "org.example.controller.DashboardController$AdminSectionSnapshot",
                "success",
                new Class<?>[]{List.class, List.class, List.class, List.class},
                List.of(admin, seller),
                List.of(ownItem),
                List.of(settlement.getDisplaySummary()),
                List.of(settlement)
        );
        invoke(adminController, "applyAdminSection", success, admin);
        assertEquals(2, field(adminController, "userTable", TableView.class).getItems().size());
        assertEquals(1, field(adminController, "pendingItemsTable", TableView.class).getItems().size());
        assertEquals(List.of(settlement.getDisplaySummary()), field(adminController, "adminSettlementList", ListView.class).getItems());

        WalletTransaction transaction = walletTransaction("TX-ADMIN", admin.getId(), 15.0, 1015.0);
        assertTrue(invoke(adminController, "walletAuditLine", transaction).toString().contains("$15.00"));

        Object failure = invokeStatic(
                "org.example.controller.DashboardController$AdminSectionSnapshot",
                "failure",
                new Class<?>[]{String.class},
                "offline"
        );
        invoke(adminController, "applyAdminSection", failure, admin);
        assertEquals(List.of("Admin data unavailable."), field(adminController, "adminSettlementList", ListView.class).getItems());
        assertEquals(List.of("offline"), field(adminController, "adminWalletAuditList", ListView.class).getItems());

        Object notAdmin = invokeStatic("org.example.controller.DashboardController$AdminSectionSnapshot", "notAdmin");
        invoke(adminController, "applyAdminSection", notAdmin, admin);
        assertTrue(field(adminController, "userTable", TableView.class).getItems().isEmpty());
    }

    @Test
    void refreshedUserAndRefreshLoopBranchesAreSafeInJavaFxThread() throws Exception {
        Bidder bidder = bidder();
        session.login(bidder);
        DashboardController controller = dashboardController();
        invoke(controller, "configureTables");
        invoke(controller, "configureRoleTabs");

        Seller refreshed = seller();
        refreshed.setFullName("Updated Seller");
        refreshed.setPhoneNumber("555-0199");
        refreshed.setAddress("99 Updated Road");
        invoke(controller, "applyRefreshedUser", refreshed);

        assertEquals("SELLER", session.getCurrentUser().orElseThrow().getRole());
        assertFalse(field(controller, "sellerTab", Tab.class).isDisabled());
        assertTrue(field(controller, "adminTab", Tab.class).isDisabled());
        assertEquals("Updated Seller", field(controller, "fullNameField", TextField.class).getText());

        JavaFxTestSupport.runAndWait(() -> {
            try {
                invoke(controller, "startRefreshLoop");
                invoke(controller, "stopRefreshLoop");
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
        assertNull(field(controller, "refreshTimeline"));
    }

    @Test
    void localSnapshotLoadersAndRefreshFailuresUseNonModalBranches() throws Exception {
        Bidder bidder = registeredBidder("dashboard_snapshot");
        session.login(bidder);
        DashboardController controller = dashboardController();
        invoke(controller, "configureTables");

        Object context = newNested(
                "org.example.controller.DashboardController$DashboardLoadContext",
                new Class<?>[]{boolean.class, String.class, User.class},
                false,
                null,
                bidder
        );
        Object snapshot = invoke(controller, "loadDashboardSnapshot", context);
        assertNotNull(snapshot);
        invoke(controller, "applyDashboardSnapshot", snapshot);
        assertEquals("Dashboard Snapshot (" + bidder.getUsername() + ")", field(controller, "signedInUserLabel", Label.class).getText());
        assertEquals("Not set", field(controller, "walletPinStatusLabel", Label.class).getText());

        assertTrue(((List<?>) invoke(controller, "loadSellerItems", context, bidder)).isEmpty());
        Object notAdminSection = invoke(controller, "loadAdminSectionSnapshot", context, bidder);
        assertEquals(false, invoke(notAdminSection, "admin"));

        Object noSelectionButtons = invoke(controller, "loadBuyerSettlementButtonState", false, null, "", bidder.getId());
        assertEquals(true, invoke(noSelectionButtons, "admitDisabled"));
        assertEquals(true, invoke(noSelectionButtons, "confirmDisabled"));

        invoke(controller, "handleDashboardRefreshFailure", "offline", false);
        assertEquals("offline", field(controller, "lastDashboardRefreshFailureMessage"));
        invoke(controller, "appendNotificationLine", "Partial data", "offline");
        assertTrue(field(controller, "notificationList", ListView.class).getItems().getFirst().toString().contains("Partial data"));

        setField(controller, "lastAuctionDetailFailureMessage", "same detail failure");
        invoke(controller, "handleAuctionDetailRefreshFailure", "same detail failure");
        setField(controller, "lastSellerDetailFailureMessage", "same seller failure");
        invoke(controller, "handleSellerDetailRefreshFailure", "same seller failure");

        User admin = registeredAdmin("dashboard_admin_snapshot");
        session.login(admin);
        DashboardController adminController = dashboardController();
        invoke(adminController, "configureTables");
        Object adminContext = newNested(
                "org.example.controller.DashboardController$DashboardLoadContext",
                new Class<?>[]{boolean.class, String.class, User.class},
                false,
                null,
                admin
        );
        Object adminSection = invoke(adminController, "loadAdminSectionSnapshot", adminContext, admin);
        assertEquals(true, invoke(adminSection, "admin"));
        invoke(adminController, "applyAdminSection", adminSection, admin);
        assertFalse(field(adminController, "userTable", TableView.class).getItems().isEmpty());
        invoke(adminController, "handleDashboardRefreshFailure", "admin offline", false);
        assertEquals(List.of("Admin data unavailable."), field(adminController, "adminSettlementList", ListView.class).getItems());
    }

    @Test
    void modalDashboardHandlersExecuteRealWalletAuctionSellerAndAdminWorkflows() throws Exception {
        Bidder bidder = registeredBidder("dashboard_modal_bidder");

        session.login(bidder);
        DashboardController bidderController = dashboardController();
        invoke(bidderController, "configureTables");
        field(bidderController, "newWalletPinField", PasswordField.class).setText(PIN);
        closeInfoDialog();
        invokeOnFx(bidderController, "handleSetWalletPin");
        assertEquals("Set", field(bidderController, "walletPinStatusLabel", Label.class).getText());

        field(bidderController, "walletPinField", PasswordField.class).setText(PIN);
        invoke(bidderController, "handleOpenWallet");
        assertEquals("$500.00", field(bidderController, "walletBalanceLabel", Label.class).getText());

        field(bidderController, "walletAccountNameField", TextField.class).setText(bidder.getFullName());
        field(bidderController, "walletProviderField", TextField.class).setText("Test Bank");
        field(bidderController, "walletAccountReferenceField", TextField.class).setText("1234567890");
        field(bidderController, "walletAccountOpeningBalanceField", TextField.class).setText("50");
        answerWalletPinThenInfo();
        invokeOnFx(bidderController, "handleAddWalletAccount");
        assertEquals(1, field(bidderController, "walletAccountTable", TableView.class).getItems().size());
        assertEquals("", field(bidderController, "walletAccountNameField", TextField.class).getText());

        field(bidderController, "walletTransferAmountField", TextField.class).setText("25");
        answerWalletPinThenInfo();
        invokeOnFx(bidderController, "handleSendWalletMoney");
        assertEquals("$475.00", field(bidderController, "walletBalanceLabel", Label.class).getText());

        field(bidderController, "walletTransferAmountField", TextField.class).setText("10");
        answerWalletPinThenInfo();
        invokeOnFx(bidderController, "handleReceiveWalletMoney");
        assertEquals("$485.00", field(bidderController, "walletBalanceLabel", Label.class).getText());

        field(bidderController, "walletAccountTopUpAmountField", TextField.class).setText("15");
        answerWalletPinThenInfo();
        invokeOnFx(bidderController, "handleTopUpWalletAccount");
        assertEquals("", field(bidderController, "walletAccountTopUpAmountField", TextField.class).getText());

        answerWalletPinThenInfo();
        invokeOnFx(bidderController, "handleSetPrimaryWalletAccount");

        trustWallet(bidder);
        JavaFxTestSupport.closeNextDialogThenCloseAlert(ButtonType.YES);
        invokeOnFx(bidderController, "handleRemoveWalletAccount");
        assertTrue(field(bidderController, "walletAccountTable", TableView.class).getItems().isEmpty());

        session.logout();
        Seller seller = registeredSeller("dashboard_modal_seller");
        session.login(seller);
        DashboardController sellerController = dashboardController();
        invoke(sellerController, "configureTables");
        field(sellerController, "sellerItemTypeChoiceBox", ChoiceBox.class).setValue("electronics");
        field(sellerController, "sellerItemNameField", TextField.class).setText("Modal Camera");
        field(sellerController, "sellerDescriptionArea", TextArea.class).setText("Camera created from dashboard test");
        field(sellerController, "sellerStartingPriceField", TextField.class).setText("120");
        field(sellerController, "sellerExtraTextField", TextField.class).setText("Brand");
        field(sellerController, "sellerExtraNumberField", TextField.class).setText("12");
        field(sellerController, "sellerPrepareMinutesField", TextField.class).setText("0");
        field(sellerController, "sellerBiddingMinutesField", TextField.class).setText("60");
        closeInfoDialog();
        invokeOnFx(sellerController, "handleAddSellerItem");
        assertEquals("", field(sellerController, "sellerItemNameField", TextField.class).getText());

        Item unsaved = item("DASHBOARD-UNSAVED", seller.getId());
        field(sellerController, "sellerItemsTable", TableView.class).setItems(FXCollections.observableArrayList(unsaved));
        field(sellerController, "sellerItemsTable", TableView.class).getSelectionModel().select(unsaved);
        invokeWithClosedDialog(sellerController, "handleStartSellerAuction");
        invokeWithClosedDialog(sellerController, "handleFinishSellerAuction");

        session.logout();
        User admin = registeredAdmin("dashboard_modal_admin");
        session.login(admin);
        DashboardController adminController = dashboardController();
        invoke(adminController, "configureTables");
        field(adminController, "userTable", TableView.class).setItems(FXCollections.observableArrayList(bidder));
        field(adminController, "userTable", TableView.class).getSelectionModel().select(bidder);
        field(adminController, "roleChoiceBox", ChoiceBox.class).setValue("SELLER");
        closeInfoDialog();
        invokeOnFx(adminController, "handleUpdateRole");
        assertEquals("SELLER", field(adminController, "roleChoiceBox", ChoiceBox.class).getValue());

        Item pendingItem = MarketplaceDashboardService.getInstance().addSellerItem(
                seller,
                "electronics",
                "Admin Approval Camera",
                "Pending approval",
                140.0,
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(30),
                "Brand",
                24
        );
        field(adminController, "pendingItemsTable", TableView.class).setItems(FXCollections.observableArrayList(pendingItem));
        field(adminController, "pendingItemsTable", TableView.class).getSelectionModel().select(pendingItem);
        closeInfoDialog();
        invokeOnFx(adminController, "handleApproveItem");
        assertEquals(ApprovalStatus.APPROVED, pendingItem.getApprovalStatus());
    }

    @Test
    void successfulDashboardAuctionHandlersExecuteLocalBidAutoBidAndSettlementBranches() throws Exception {
        registeredAdmin("dashboard_success_admin");
        Bidder bidder = registeredBidder("dashboard_success_bidder");
        Seller seller = registeredSeller("dashboard_success_seller");
        dashboardService.setWalletPin(bidder, PIN);
        dashboardService.setWalletPin(seller, PIN);
        Item item = dashboardService.addSellerItem(
                seller,
                "electronics",
                "Dashboard Success Camera",
                "Dashboard successful flow item",
                100.0,
                LocalDateTime.now().minusMinutes(2),
                LocalDateTime.now().plusMinutes(20),
                "Brand",
                12
        );
        dashboardService.updateItemApproval(item.getId(), ApprovalStatus.APPROVED);
        assertTrue(dashboardService.startAuction(seller, item.getId()));

        session.login(bidder);
        DashboardController bidderController = dashboardController();
        invoke(bidderController, "configureTables");
        AuctionEligibilityEntry entry = auctionEntry(item.getId(), item.getItemName(), "RUNNING", 100.0, 110.0, true, false, 120L);
        TableView<AuctionEligibilityEntry> auctionTable = field(bidderController, "auctionTable");
        auctionTable.setItems(FXCollections.observableArrayList(entry));
        auctionTable.getSelectionModel().select(entry);
        setField(bidderController, "selectedAuctionId", item.getId());
        trustWallet(bidder);

        closeInfoDialog();
        invokeOnFx(bidderController, "handleConfirmAuctionEntry");
        assertTrue(dashboardService.hasConfirmedEntryDeposit(item.getId(), bidder));

        AuctionEligibilityEntry entered = auctionEntry(item.getId(), item.getItemName(), "RUNNING", 100.0, 110.0, true, true, 120L);
        auctionTable.setItems(FXCollections.observableArrayList(entered));
        auctionTable.getSelectionModel().select(entered);
        field(bidderController, "bidAmountField", TextField.class).setText("130.00");
        invokeOnFx(bidderController, "handlePlaceBidFromDashboard");
        assertTrue(dashboardService.getBidHistory(item.getId()).stream()
                .anyMatch(bid -> bid.getBidderId().equals(bidder.getId()) && bid.getAmount() >= 130.0));

        field(bidderController, "autoBidMaxField", TextField.class).setText("220.00");
        field(bidderController, "autoBidIncrementField", TextField.class).setText("10.00");
        invokeOnFx(bidderController, "handleRegisterAutoBidFromDashboard");
        assertEquals("", field(bidderController, "autoBidMaxField", TextField.class).getText());

        invokeOnFx(bidderController, "handleDisableAutoBidFromDashboard");
        assertTrue(field(bidderController, "bidNotificationList", ListView.class).getItems().stream()
                .anyMatch(line -> line.toString().contains("Auto-bid disabled")));

        assertTrue(dashboardService.finishAuction(seller, item.getId()));
        Object admitButtons = invoke(bidderController, "loadBuyerSettlementButtonState", false, null, item.getId(), bidder.getId());
        assertEquals(false, invoke(admitButtons, "admitDisabled"));
        assertEquals(true, invoke(admitButtons, "confirmDisabled"));
        closeInfoDialog();
        invokeOnFx(bidderController, "handleAdmitDashboardResult");
        assertEquals(AuctionSettlementStatus.AWAITING_SELLER_CONFIRMATION,
                dashboardService.getSettlement(item.getId()).orElseThrow().getStatus());

        session.login(seller);
        DashboardController sellerController = dashboardController();
        invoke(sellerController, "configureTables");
        field(sellerController, "sellerItemsTable", TableView.class).setItems(FXCollections.observableArrayList(item));
        field(sellerController, "sellerItemsTable", TableView.class).getSelectionModel().select(item);
        trustWallet(seller);
        closeInfoDialog();
        invokeOnFx(sellerController, "handleSellerMarkShipped");
        assertEquals(AuctionSettlementStatus.AWAITING_BUYER_CONFIRMATION,
                dashboardService.getSettlement(item.getId()).orElseThrow().getStatus());
        Object confirmButtons = invoke(sellerController, "loadBuyerSettlementButtonState", false, null, item.getId(), bidder.getId());
        assertEquals(true, invoke(confirmButtons, "admitDisabled"));
        assertEquals(false, invoke(confirmButtons, "confirmDisabled"));

        session.login(bidder);
        DashboardController confirmController = dashboardController();
        invoke(confirmController, "configureTables");
        setField(confirmController, "selectedAuctionId", item.getId());
        trustWallet(bidder);
        closeInfoDialog();
        invokeOnFx(confirmController, "handleConfirmDashboardReceived");
        assertEquals(AuctionSettlementStatus.PAYMENT_RELEASED,
                dashboardService.getSettlement(item.getId()).orElseThrow().getStatus());
    }

    @Test
    void asyncDashboardDetailRefreshersApplyBidHistoryForSelectedAuctionAndSellerItem() throws Exception {
        Bidder bidder = registeredBidder("dashboard_async_bidder");
        Seller seller = registeredSeller("dashboard_async_seller");
        dashboardService.setWalletPin(bidder, PIN);
        dashboardService.setWalletPin(seller, PIN);
        Item item = dashboardService.addSellerItem(
                seller,
                "electronics",
                "Dashboard Async Camera",
                "Async detail refresh item",
                100.0,
                LocalDateTime.now().minusMinutes(2),
                LocalDateTime.now().plusMinutes(20),
                "Brand",
                12
        );
        dashboardService.updateItemApproval(item.getId(), ApprovalStatus.APPROVED);
        assertTrue(dashboardService.startAuction(seller, item.getId()));
        assertTrue(dashboardService.confirmAuctionEntry(item.getId(), bidder, PIN).accepted());
        assertTrue(dashboardService.placeBidWithDeposit(item.getId(), bidder, 130.0, PIN).accepted());
        assertTrue(dashboardService.finishAuction(seller, item.getId()));

        DashboardController bidderController = null;
        DashboardController sellerController = null;
        try {
            session.login(bidder);
            bidderController = dashboardController();
            invoke(bidderController, "configureTables");
            AuctionEligibilityEntry entry = auctionEntry(
                    item.getId(),
                    item.getItemName(),
                    "RUNNING",
                    130.0,
                    140.0,
                    true,
                    true,
                    120L
            );
            TableView<AuctionEligibilityEntry> auctionTable = field(bidderController, "auctionTable");
            auctionTable.setItems(FXCollections.observableArrayList(entry));
            auctionTable.getSelectionModel().select(entry);
            setField(bidderController, "selectedAuctionId", item.getId());
            setField(bidderController, "refreshActive", true);

            invoke(bidderController, "refreshSelectedAuctionDetailAsync", entry);

            DashboardController finalBidderController = bidderController;
            waitForFxCondition(() -> listViewHasItems(finalBidderController, "bidNotificationList"));
            assertTrue(field(bidderController, "currentWinnerLabel", Label.class).getText()
                    .contains(bidder.getFullName()));
            assertFalse(field(bidderController, "admitDashboardResultButton", Button.class).isDisabled());

            session.login(seller);
            sellerController = dashboardController();
            invoke(sellerController, "configureTables");
            TableView<Item> sellerItemsTable = field(sellerController, "sellerItemsTable");
            sellerItemsTable.setItems(FXCollections.observableArrayList(item));
            sellerItemsTable.getSelectionModel().select(item);
            setField(sellerController, "refreshActive", true);

            invoke(sellerController, "refreshSellerSelectionAsync", item);

            DashboardController finalSellerController = sellerController;
            waitForFxCondition(() -> listViewHasItems(finalSellerController, "sellerBidHistoryList"));
            assertTrue(field(sellerController, "sellerBidHistoryList", ListView.class).getItems().getFirst()
                    .toString()
                    .contains("$130.00"));
        } finally {
            if (bidderController != null) {
                invoke(bidderController, "stopRefreshLoop");
            }
            if (sellerController != null) {
                invoke(sellerController, "stopRefreshLoop");
            }
        }
    }

    @Test
    void dashboardRejectedWalletAuctionAndAutoBidActionsUseRealServiceBranches() throws Exception {
        Bidder bidder = registeredBidder("dashboard_reject_bidder");
        Seller seller = registeredSeller("dashboard_reject_seller");
        dashboardService.setWalletPin(bidder, PIN);
        dashboardService.setWalletPin(seller, PIN);
        Item item = dashboardService.addSellerItem(
                seller,
                "electronics",
                "Dashboard Rejection Camera",
                "Rejected dashboard branch item",
                100.0,
                LocalDateTime.now().minusMinutes(2),
                LocalDateTime.now().plusMinutes(20),
                "Brand",
                12
        );
        dashboardService.updateItemApproval(item.getId(), ApprovalStatus.APPROVED);
        assertTrue(dashboardService.startAuction(seller, item.getId()));

        session.login(bidder);
        DashboardController bidderController = dashboardController();
        invoke(bidderController, "configureTables");

        field(bidderController, "walletAccountNameField", TextField.class).setText(bidder.getFullName());
        field(bidderController, "walletProviderField", TextField.class).setText("Test Bank");
        field(bidderController, "walletAccountReferenceField", TextField.class).setText("reject-123");
        field(bidderController, "walletAccountOpeningBalanceField", TextField.class).setText("-1");
        closeInfoDialog();
        invokeOnFx(bidderController, "handleAddWalletAccount");

        field(bidderController, "walletAccountOpeningBalanceField", TextField.class).setText("15");
        JavaFxTestSupport.answerNextPasswordDialog("", false, ButtonType.CANCEL);
        invokeOnFx(bidderController, "handleAddWalletAccount");
        assertTrue(field(bidderController, "walletAccountTable", TableView.class).getItems().isEmpty());

        AuctionEligibilityEntry entry = auctionEntry(
                item.getId(),
                item.getItemName(),
                "RUNNING",
                100.0,
                110.0,
                true,
                false,
                120L
        );
        TableView<AuctionEligibilityEntry> auctionTable = field(bidderController, "auctionTable");
        auctionTable.setItems(FXCollections.observableArrayList(entry));
        auctionTable.getSelectionModel().select(entry);
        setField(bidderController, "selectedAuctionId", item.getId());
        trustWallet(bidder);

        field(bidderController, "bidAmountField", TextField.class).setText("130");
        closeInfoDialog();
        invokeOnFx(bidderController, "handlePlaceBidFromDashboard");
        assertTrue(dashboardService.getBidHistory(item.getId()).isEmpty());

        field(bidderController, "autoBidMaxField", TextField.class).setText("220");
        field(bidderController, "autoBidIncrementField", TextField.class).setText("10");
        closeInfoDialog();
        invokeOnFx(bidderController, "handleRegisterAutoBidFromDashboard");
        assertEquals("220", field(bidderController, "autoBidMaxField", TextField.class).getText());

        session.login(seller);
        DashboardController sellerController = dashboardController();
        invoke(sellerController, "configureTables");
        TableView<AuctionEligibilityEntry> sellerAuctionTable = field(sellerController, "auctionTable");
        sellerAuctionTable.setItems(FXCollections.observableArrayList(entry));
        sellerAuctionTable.getSelectionModel().select(entry);
        trustWallet(seller);

        closeInfoDialog();
        invokeOnFx(sellerController, "handleConfirmAuctionEntry");
        assertFalse(dashboardService.hasConfirmedEntryDeposit(item.getId(), seller));
    }

    @Test
    void apiDashboardBranchesUseHttpBackedClientForAdminSellerAndDetailLoaders() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", new AuctionApiHandler(
                AuthenticationService.getInstance(),
                AuctionWorkflowService.getInstance(),
                new ApiSessionService(),
                new AuctionRealtimeBroker()
        ));
        server.start();

        String previousBaseUrl = System.getProperty("auction.api.baseUrl");
        try {
            System.setProperty("auction.api.baseUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/api");
            AuctionApiClient client = newApiClient();
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            var adminAuth = client.login("admin", "admin123");
            var bidderAuth = client.registerManualBidder(
                    "dash_api_bid_" + suffix,
                    "secret123",
                    "dash_api_bid_" + suffix + "@test.local",
                    "Dashboard API Bidder " + suffix
            );

            session.login(adminAuth.user(), adminAuth.token());
            DashboardController controller = dashboardController();
            setField(controller, "apiClient", client);
            invoke(controller, "configureTables");
            invoke(controller, "configureRoleTabs");

            Object context = newNested(
                    "org.example.controller.DashboardController$DashboardLoadContext",
                    new Class<?>[]{boolean.class, String.class, User.class},
                    true,
                    adminAuth.token(),
                    adminAuth.user()
            );
            Object snapshot = invoke(controller, "loadDashboardSnapshot", context);
            assertNotNull(snapshot);

            Object adminSection = invoke(controller, "loadAdminSectionSnapshot", context, adminAuth.user());
            assertEquals(true, invoke(adminSection, "admin"));
            invoke(controller, "applyAdminSection", adminSection, adminAuth.user());
            assertFalse(field(controller, "userTable", TableView.class).getItems().isEmpty());

            field(controller, "userTable", TableView.class).setItems(FXCollections.observableArrayList(bidderAuth.user()));
            field(controller, "userTable", TableView.class).getSelectionModel().select(bidderAuth.user());
            invokeOnFx(controller, "handleLoadWalletAudit");
            assertFalse(field(controller, "adminWalletAuditList", ListView.class).getItems().isEmpty());

            field(controller, "roleChoiceBox", ChoiceBox.class).setValue("SELLER");
            closeInfoDialog();
            invokeOnFx(controller, "handleUpdateRole");
            assertEquals("SELLER", client.getCurrentUser(bidderAuth.token()).getRole());

            field(controller, "sellerItemTypeChoiceBox", ChoiceBox.class).setValue("electronics");
            field(controller, "sellerItemNameField", TextField.class).setText("Dashboard API Camera " + suffix);
            field(controller, "sellerDescriptionArea", TextArea.class).setText("API-created item");
            field(controller, "sellerStartingPriceField", TextField.class).setText("120");
            field(controller, "sellerExtraTextField", TextField.class).setText("Brand");
            field(controller, "sellerExtraNumberField", TextField.class).setText("12");
            field(controller, "sellerPrepareMinutesField", TextField.class).setText("0");
            field(controller, "sellerBiddingMinutesField", TextField.class).setText("60");
            closeInfoDialog();
            invokeOnFx(controller, "handleAddSellerItem");

            Item apiItem = client.getSellerItems(adminAuth.token()).stream()
                    .filter(item -> item.getItemName().equals("Dashboard API Camera " + suffix))
                    .findFirst()
                    .orElseThrow();
            client.updateItemApproval(adminAuth.token(), apiItem.getId(), ApprovalStatus.APPROVED);

            field(controller, "sellerItemsTable", TableView.class).setItems(FXCollections.observableArrayList(apiItem));
            field(controller, "sellerItemsTable", TableView.class).getSelectionModel().select(apiItem);
            closeInfoDialog();
            invokeOnFx(controller, "handleStartSellerAuction");
            closeInfoDialog();
            invokeOnFx(controller, "handleFinishSellerAuction");

            Object auctionContext = newNested(
                    "org.example.controller.DashboardController$AuctionSelectionLoadContext",
                    new Class<?>[]{long.class, boolean.class, String.class, String.class, String.class},
                    7L,
                    true,
                    adminAuth.token(),
                    adminAuth.user().getId(),
                    apiItem.getId()
            );
            Object auctionSnapshot = invoke(controller, "loadAuctionDetailSectionSnapshot", auctionContext);
            assertEquals(apiItem.getId(), invoke(auctionSnapshot, "itemId"));

            Object sellerContext = newNested(
                    "org.example.controller.DashboardController$SellerSelectionLoadContext",
                    new Class<?>[]{long.class, boolean.class, String.class, String.class},
                    8L,
                    true,
                    adminAuth.token(),
                    apiItem.getId()
            );
            Object sellerSnapshot = invoke(controller, "loadSellerSelectionDetailSnapshot", sellerContext);
            assertEquals(apiItem.getId(), invoke(sellerSnapshot, "itemId"));
            assertNotNull(invoke(controller, "bidHistory", true, adminAuth.token(), apiItem.getId()));
            Object disabledButtons = invoke(controller, "loadBuyerSettlementButtonState",
                    true, adminAuth.token(), apiItem.getId(), adminAuth.user().getId());
            assertEquals(true, invoke(disabledButtons, "admitDisabled"));
            assertEquals(true, invoke(disabledButtons, "confirmDisabled"));
        } finally {
            if (previousBaseUrl == null) {
                System.clearProperty("auction.api.baseUrl");
            } else {
                System.setProperty("auction.api.baseUrl", previousBaseUrl);
            }
            server.stop(0);
        }
    }

    @Test
    void apiDashboardProfileWalletAndRecoveryHandlersUseHttpBackedClientBranches() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", new AuctionApiHandler(
                AuthenticationService.getInstance(),
                AuctionWorkflowService.getInstance(),
                new ApiSessionService(),
                new AuctionRealtimeBroker()
        ));
        server.start();

        String previousBaseUrl = System.getProperty("auction.api.baseUrl");
        try {
            System.setProperty("auction.api.baseUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/api");
            AuctionApiClient client = newApiClient();
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            var auth = client.registerManualBidder(
                    "dash_wallet_" + suffix,
                    "secret123",
                    "dash_wallet_" + suffix + "@test.local",
                    "Dashboard API Wallet"
            );
            Bidder apiBidder = (Bidder) auth.user();
            apiBidder.setBalance(500.0);
            AuthenticationService.getInstance().updateUser(apiBidder);

            session.login(auth.user(), auth.token());
            DashboardController controller = dashboardController();
            setField(controller, "apiClient", client);
            invoke(controller, "configureTables");

            field(controller, "fullNameField", TextField.class).setText("Dashboard API Wallet Updated");
            field(controller, "phoneField", TextField.class).setText("555-0300");
            field(controller, "addressArea", TextArea.class).setText("API Wallet Address");
            closeInfoDialog();
            invokeOnFx(controller, "handleSaveProfile");
            assertEquals("Dashboard API Wallet Updated", session.getCurrentUser().orElseThrow().getFullName());

            field(controller, "avatarUrlField", TextField.class).setText("https://example.test/api-wallet.png");
            closeInfoDialog();
            invokeOnFx(controller, "handleSaveAvatar");
            assertEquals("https://example.test/api-wallet.png", session.getCurrentUser().orElseThrow().getAvatarUrl());

            field(controller, "newWalletPinField", PasswordField.class).setText(PIN);
            closeInfoDialog();
            invokeOnFx(controller, "handleSetWalletPin");
            assertEquals("Set", field(controller, "walletPinStatusLabel", Label.class).getText());

            field(controller, "walletPinField", PasswordField.class).setText(PIN);
            invokeOnFx(controller, "handleOpenWallet");
            assertEquals("$500.00", field(controller, "walletBalanceLabel", Label.class).getText());

            closeInfoDialog();
            invokeOnFx(controller, "handleRequestWalletPinRecovery");
            @SuppressWarnings("unchecked")
            java.util.Map<String, String> recoveryCodes = (java.util.Map<String, String>) field(
                    WalletService.getInstance(),
                    "recoveryCodesByUserId"
            );
            recoveryCodes.put(auth.user().getId(), CredentialHasher.hash("999999"));
            field(controller, "recoveryCodeField", TextField.class).setText("999999");
            field(controller, "newWalletPinField", PasswordField.class).setText("1357");
            closeInfoDialog();
            invokeOnFx(controller, "handleResetWalletPin");

            var authorization = dashboardService.authorizeWallet(session.getCurrentUser().orElseThrow(), "1357", Duration.ofMinutes(5));
            session.trustWalletAuthorization(auth.user().getId(), authorization.token(), authorization.expiresAt());
            field(controller, "walletAccountNameField", TextField.class).setText("Dashboard API Wallet Updated");
            field(controller, "walletProviderField", TextField.class).setText("API Bank");
            field(controller, "walletAccountReferenceField", TextField.class).setText("123456789012");
            field(controller, "walletAccountOpeningBalanceField", TextField.class).setText("25");
            closeInfoDialog();
            invokeOnFx(controller, "handleAddWalletAccount");
            assertEquals(1, field(controller, "walletAccountTable", TableView.class).getItems().size());
        } finally {
            if (previousBaseUrl == null) {
                System.clearProperty("auction.api.baseUrl");
            } else {
                System.setProperty("auction.api.baseUrl", previousBaseUrl);
            }
            server.stop(0);
        }
    }

    @Test
    void dashboardLocalAdminFilterSellerAndRefreshFailureBranchesUseRealControls() throws Exception {
        Bidder bidder = registeredBidder("dashboard_filter_bidder");
        Seller seller = registeredSeller("dashboard_filter_seller");
        User admin = registeredAdmin("dashboard_filter_admin");

        session.login(admin);
        DashboardController controller = dashboardController();
        invoke(controller, "configureTables");
        invoke(controller, "bindCurrentUserFields");

        field(controller, "userTable", TableView.class).setItems(FXCollections.observableArrayList(admin));
        field(controller, "userTable", TableView.class).getSelectionModel().select(admin);
        field(controller, "roleChoiceBox", ChoiceBox.class).setValue("SELLER");
        closeInfoDialog();
        invokeOnFx(controller, "handleUpdateRole");
        assertEquals("SELLER", session.getCurrentUser().orElseThrow().getRole());

        field(controller, "sellerItemTypeChoiceBox", ChoiceBox.class).setValue("electronics");
        field(controller, "sellerStartingPriceField", TextField.class).setText("50");
        field(controller, "sellerExtraNumberField", TextField.class).setText("4");
        field(controller, "sellerPrepareMinutesField", TextField.class).setText("0");
        field(controller, "sellerBiddingMinutesField", TextField.class).setText("15");
        closeInfoDialog();
        invokeOnFx(controller, "handleAddSellerItem");

        field(controller, "sellerItemNameField", TextField.class).setText("Invalid Session Camera");
        field(controller, "sellerDescriptionArea", TextArea.class).setText("Invalid session data");
        field(controller, "sellerStartingPriceField", TextField.class).setText("-1");
        closeInfoDialog();
        invokeOnFx(controller, "handleAddSellerItem");

        Item preserved = item("DASH-FILTER-ITEM", seller.getId());
        field(controller, "sellerItemsTable", TableView.class).setItems(FXCollections.observableArrayList(preserved));
        field(controller, "sellerItemsTable", TableView.class).getSelectionModel().select(preserved);
        invoke(controller, "applySellerItems", List.of(preserved), session.getCurrentUser().orElseThrow());
        assertSame(preserved, field(controller, "sellerItemsTable", TableView.class).getSelectionModel().getSelectedItem());
        invoke(controller, "applySellerItems", List.of(preserved), bidder);
        assertTrue(field(controller, "sellerItemsTable", TableView.class).getItems().isEmpty());

        AuctionEligibilityEntry running = auctionEntry("FILTER-1", "Filter Camera", "RUNNING", 100.0, 110.0, true, false, 60L);
        AuctionEligibilityEntry finished = auctionEntry("FILTER-2", "Filter Print", "FINISHED", 90.0, 100.0, false, true, 0L);
        setField(controller, "selectedAuctionId", "FILTER-MISSING");
        field(controller, "dashboardAuctionSearchField", TextField.class).setText("filter");
        field(controller, "dashboardAuctionOpenOnlyCheckBox", CheckBox.class).setSelected(true);
        invoke(controller, "applyAuctionEntries", List.of(running, finished));
        assertEquals(List.of("FILTER-1"), field(controller, "auctionTable", TableView.class).getItems().stream()
                .map(entry -> ((AuctionEligibilityEntry) entry).getItemId())
                .toList());

        field(controller, "dashboardAuctionSearchField", TextField.class).setText("nothing");
        field(controller, "dashboardAuctionOpenOnlyCheckBox", CheckBox.class).setSelected(false);
        invoke(controller, "applyAuctionFilters");
        assertEquals("Showing 0 of 2 auction sessions", field(controller, "auctionResultsSummaryLabel", Label.class).getText());

        CompletableFuture<Boolean> refreshDialog = JavaFxTestSupport.closeNextDialogAndTrack(
                ButtonType.OK,
                TimeUnit.SECONDS.toMillis(5)
        );
        invokeOnFx(controller, "handleDashboardRefreshFailure", "initial dashboard failure", true);
        assertTrue(refreshDialog.get(6, TimeUnit.SECONDS));
        invoke(controller, "handleDashboardRefreshFailure", "initial dashboard failure", true);
        invoke(controller, "handleDashboardRefreshFailure", "", false);
        assertEquals("Dashboard data is temporarily unavailable.", field(controller, "lastDashboardRefreshFailureMessage"));

        invoke(controller, "stopRefreshLoop");
    }

    @Test
    void modalHandlersReportMissingSelectionsAndInvalidInputWithoutChangingState() throws Exception {
        Bidder bidder = registeredBidder("dashboard_validation_bidder");
        session.login(bidder);
        DashboardController bidderController = dashboardController();
        invoke(bidderController, "configureTables");

        invokeWithClosedDialog(bidderController, "handleSetPrimaryWalletAccount");
        invokeWithClosedDialog(bidderController, "handleRemoveWalletAccount");
        invokeWithClosedDialog(bidderController, "handleReceiveWalletMoney");
        invokeWithClosedDialog(bidderController, "handleSendWalletMoney");
        invokeWithClosedDialog(bidderController, "handleTopUpWalletAccount");
        invokeWithClosedDialog(bidderController, "handleOpenAuctionDetail");
        invokeWithClosedDialog(bidderController, "handleToggleSelectedAuctionWatch");
        invokeWithClosedDialog(bidderController, "handleConfirmAuctionEntry");
        invokeWithClosedDialog(bidderController, "handlePlaceBidFromDashboard");
        invokeWithClosedDialog(bidderController, "handleRegisterAutoBidFromDashboard");
        invokeWithClosedDialog(bidderController, "handleDisableAutoBidFromDashboard");
        invokeWithClosedDialog(bidderController, "handleAdmitDashboardResult");
        invokeWithClosedDialog(bidderController, "handleConfirmDashboardReceived");
        invokeWithClosedDialog(bidderController, "handleAddSellerItem");

        assertNull(field(bidderController, "selectedAuctionId"));
        assertTrue(field(bidderController, "walletAccountTable", TableView.class).getItems().isEmpty());

        WalletLinkedAccount primary = walletAccount("ACCOUNT-VALIDATION", bidder.getId(), true, 100.0);
        WalletSummary wallet = new WalletSummary(
                bidder.getId(),
                500.0,
                0.0,
                500.0,
                true,
                List.of(primary),
                List.of()
        );
        setField(bidderController, "openedWalletSummary", wallet);
        invoke(bidderController, "refreshWallet", wallet);

        field(bidderController, "walletAccountTopUpAmountField", TextField.class).setText("abc");
        invokeWithClosedDialog(bidderController, "handleTopUpWalletAccount");
        assertEquals("abc", field(bidderController, "walletAccountTopUpAmountField", TextField.class).getText());

        field(bidderController, "walletTransferAmountField", TextField.class).setText("abc");
        invokeWithClosedDialog(bidderController, "handleReceiveWalletMoney");
        assertEquals("abc", field(bidderController, "walletTransferAmountField", TextField.class).getText());

        AuctionEligibilityEntry runningEntry = auctionEntry("A-VALIDATION", "Validation Camera", "RUNNING", 100.0, 110.0, true, true, 120L);
        field(bidderController, "auctionTable", TableView.class).setItems(FXCollections.observableArrayList(runningEntry));
        field(bidderController, "auctionTable", TableView.class).getSelectionModel().select(runningEntry);
        setField(bidderController, "selectedAuctionId", runningEntry.getItemId());

        field(bidderController, "bidAmountField", TextField.class).setText("bad");
        invokeWithClosedDialog(bidderController, "handlePlaceBidFromDashboard");
        field(bidderController, "autoBidMaxField", TextField.class).setText("bad");
        invokeWithClosedDialog(bidderController, "handleRegisterAutoBidFromDashboard");
        field(bidderController, "autoBidMaxField", TextField.class).setText("150");
        field(bidderController, "autoBidIncrementField", TextField.class).setText("-1");
        invokeWithClosedDialog(bidderController, "handleRegisterAutoBidFromDashboard");

        session.logout();
        Seller seller = registeredSeller("dashboard_validation_seller");
        session.login(seller);
        DashboardController sellerController = dashboardController();
        invoke(sellerController, "configureTables");

        invokeWithClosedDialog(sellerController, "handleStartSellerAuction");
        invokeWithClosedDialog(sellerController, "handleFinishSellerAuction");
        invokeWithClosedDialog(sellerController, "handleSellerMarkShipped");
        field(sellerController, "sellerStartingPriceField", TextField.class).setText("not-a-price");
        invokeWithClosedDialog(sellerController, "handleAddSellerItem");

        session.logout();
        Admin admin = admin();
        session.login(admin);
        DashboardController adminController = dashboardController();
        invoke(adminController, "configureTables");

        invokeWithClosedDialog(adminController, "handleUpdateRole");
        invokeWithClosedDialog(adminController, "handleLoadWalletAudit");
        invokeWithClosedDialog(adminController, "handleApproveItem");
        invokeWithClosedDialog(adminController, "handleRejectItem");
    }

    private DashboardController dashboardController() throws Exception {
        DashboardController controller = new DashboardController();
        Tab sellerTab = new Tab("Seller");
        Tab adminTab = new Tab("Admin");
        setField(controller, "dashboardTabPane", new TabPane(new Tab("Dashboard"), sellerTab, adminTab));
        setField(controller, "sellerTab", sellerTab);
        setField(controller, "adminTab", adminTab);
        setField(controller, "signedInUserLabel", new Label());
        setField(controller, "testConnectionButton", new Button("Test"));
        setField(controller, "roleLabel", new Label());
        setField(controller, "emailLabel", new Label());
        setField(controller, "avatarPreviewLabel", new Label());
        setField(controller, "balanceLabel", new Label());
        setField(controller, "lockedBalanceLabel", new Label());
        setField(controller, "availableBalanceLabel", new Label());
        setField(controller, "overviewAuctionCountLabel", new Label());
        setField(controller, "overviewRunningCountLabel", new Label());
        setField(controller, "overviewEnteredCountLabel", new Label());
        setField(controller, "overviewEligibleCountLabel", new Label());
        setField(controller, "notificationList", new ListView<String>());
        setField(controller, "fullNameField", new TextField());
        setField(controller, "phoneField", new TextField());
        setField(controller, "addressArea", new TextArea());
        setField(controller, "avatarUrlField", new TextField());
        setField(controller, "walletBalanceLabel", new Label());
        setField(controller, "walletLockedLabel", new Label());
        setField(controller, "walletAvailableLabel", new Label());
        setField(controller, "walletPinStatusLabel", new Label());
        setField(controller, "walletPinField", new PasswordField());
        setField(controller, "rememberWalletPinCheckBox", new CheckBox());
        setField(controller, "newWalletPinField", new PasswordField());
        setField(controller, "setWalletPinButton", new Button());
        setField(controller, "recoveryCodeField", new TextField());
        setField(controller, "walletTransactionTable", new TableView<WalletTransaction>());
        setField(controller, "walletTransactionTimeColumn", new TableColumn<WalletTransaction, String>());
        setField(controller, "walletTransactionTypeColumn", new TableColumn<WalletTransaction, String>());
        setField(controller, "walletTransactionAmountColumn", new TableColumn<WalletTransaction, String>());
        setField(controller, "walletTransactionBalanceColumn", new TableColumn<WalletTransaction, String>());
        setField(controller, "walletTransactionNoteColumn", new TableColumn<WalletTransaction, String>());
        setField(controller, "walletTransferAmountField", new TextField());
        setField(controller, "walletAccountTable", new TableView<WalletLinkedAccount>());
        setField(controller, "walletAccountPrimaryColumn", new TableColumn<WalletLinkedAccount, String>());
        setField(controller, "walletAccountProviderColumn", new TableColumn<WalletLinkedAccount, String>());
        setField(controller, "walletAccountNameColumn", new TableColumn<WalletLinkedAccount, String>());
        setField(controller, "walletAccountReferenceColumn", new TableColumn<WalletLinkedAccount, String>());
        setField(controller, "walletAccountBalanceColumn", new TableColumn<WalletLinkedAccount, String>());
        setField(controller, "walletAccountNameField", new TextField());
        setField(controller, "walletProviderField", new TextField());
        setField(controller, "walletAccountReferenceField", new TextField());
        setField(controller, "walletAccountOpeningBalanceField", new TextField());
        setField(controller, "walletAccountTopUpAmountField", new TextField());
        setField(controller, "auctionTable", new TableView<AuctionEligibilityEntry>());
        setField(controller, "auctionWatchColumn", new TableColumn<AuctionEligibilityEntry, String>());
        setField(controller, "auctionNameColumn", new TableColumn<AuctionEligibilityEntry, String>());
        setField(controller, "auctionStatusColumn", new TableColumn<AuctionEligibilityEntry, String>());
        setField(controller, "auctionCurrentPriceColumn", new TableColumn<AuctionEligibilityEntry, Double>());
        setField(controller, "auctionMinimumBidColumn", new TableColumn<AuctionEligibilityEntry, Double>());
        setField(controller, "auctionRequiredDepositColumn", new TableColumn<AuctionEligibilityEntry, Double>());
        setField(controller, "auctionAvailableBalanceColumn", new TableColumn<AuctionEligibilityEntry, Double>());
        setField(controller, "auctionTimeRemainingColumn", new TableColumn<AuctionEligibilityEntry, String>());
        setField(controller, "auctionEndTimeColumn", new TableColumn<AuctionEligibilityEntry, String>());
        setField(controller, "auctionEligibleColumn", new TableColumn<AuctionEligibilityEntry, String>());
        setField(controller, "dashboardAuctionSearchField", new TextField());
        setField(controller, "dashboardAuctionStatusFilterChoiceBox", new ChoiceBox<String>());
        setField(controller, "dashboardAuctionSortChoiceBox", new ChoiceBox<String>());
        setField(controller, "dashboardAuctionOpenOnlyCheckBox", new CheckBox());
        setField(controller, "dashboardWatchedOnlyCheckBox", new CheckBox());
        setField(controller, "dashboardEligibleOnlyCheckBox", new CheckBox());
        setField(controller, "auctionResultsSummaryLabel", new Label());
        setField(controller, "selectedAuctionLabel", new Label());
        setField(controller, "selectedAuctionDepositLabel", new Label());
        setField(controller, "selectedAuctionTimeRemainingLabel", new Label());
        setField(controller, "selectedAuctionEndTimeLabel", new Label());
        setField(controller, "currentWinnerLabel", new Label());
        setField(controller, "bidNotificationList", new ListView<String>());
        setField(controller, "confirmAuctionEntryButton", new Button());
        setField(controller, "watchSelectedAuctionButton", new Button());
        setField(controller, "bidHistoryChart", new LineChart<String, Number>(new CategoryAxis(), new NumberAxis()));
        setField(controller, "bidAmountField", new TextField());
        setField(controller, "bidPlusTenButton", new Button());
        setField(controller, "bidPlusFiftyButton", new Button());
        setField(controller, "bidPlusHundredButton", new Button());
        setField(controller, "autoBidMaxField", new TextField());
        setField(controller, "autoBidIncrementField", new TextField());
        setField(controller, "bidEntryTimeRemainingLabel", new Label());
        setField(controller, "placeDashboardBidButton", new Button());
        setField(controller, "registerAutoBidButton", new Button());
        setField(controller, "disableAutoBidButton", new Button());
        setField(controller, "admitDashboardResultButton", new Button());
        setField(controller, "confirmDashboardReceivedButton", new Button());
        setField(controller, "sellerItemTypeChoiceBox", new ChoiceBox<String>());
        setField(controller, "sellerItemNameField", new TextField());
        setField(controller, "sellerDescriptionArea", new TextArea());
        setField(controller, "sellerStartingPriceField", new TextField());
        setField(controller, "sellerExtraTextField", new TextField());
        setField(controller, "sellerExtraNumberField", new TextField());
        setField(controller, "sellerPrepareMinutesField", new TextField());
        setField(controller, "sellerBiddingMinutesField", new TextField());
        setField(controller, "sellerItemsTable", new TableView<Item>());
        setField(controller, "sellerItemNameColumn", new TableColumn<Item, String>());
        setField(controller, "sellerItemStatusColumn", new TableColumn<Item, ApprovalStatus>());
        setField(controller, "sellerItemCurrentPriceColumn", new TableColumn<Item, Double>());
        setField(controller, "sellerAuctionStartColumn", new TableColumn<Item, String>());
        setField(controller, "sellerAuctionEndColumn", new TableColumn<Item, String>());
        setField(controller, "sellerBidHistoryList", new ListView<String>());
        setField(controller, "sellerStartAuctionButton", new Button());
        setField(controller, "sellerFinishAuctionButton", new Button());
        setField(controller, "sellerMarkShippedButton", new Button());
        setField(controller, "userTable", new TableView<User>());
        setField(controller, "adminUsernameColumn", new TableColumn<User, String>());
        setField(controller, "adminFullNameColumn", new TableColumn<User, String>());
        setField(controller, "adminEmailColumn", new TableColumn<User, String>());
        setField(controller, "adminRoleColumn", new TableColumn<User, String>());
        setField(controller, "adminAccountStatusColumn", new TableColumn<User, String>());
        setField(controller, "roleChoiceBox", new ChoiceBox<String>());
        setField(controller, "pendingItemsTable", new TableView<Item>());
        setField(controller, "pendingItemNameColumn", new TableColumn<Item, String>());
        setField(controller, "pendingSellerColumn", new TableColumn<Item, String>());
        setField(controller, "pendingStatusColumn", new TableColumn<Item, ApprovalStatus>());
        setField(controller, "adminSettlementList", new ListView<String>());
        setField(controller, "adminWalletAuditList", new ListView<String>());
        return controller;
    }

    private static Bidder bidder() {
        Bidder bidder = new Bidder("DASH-BIDDER", "dashboard-bidder", "hash", "bidder@test.local", 1200.0);
        bidder.setRole("BIDDER");
        bidder.setFullName("Dashboard Bidder");
        bidder.setPhoneNumber("555-0100");
        bidder.setAddress("12 Test Street");
        bidder.setAvatarUrl("https://example.test/avatar.png");
        return bidder;
    }

    private static Seller seller() {
        Seller seller = new Seller("DASH-SELLER", "dashboard-seller", "hash", "seller@test.local");
        seller.setRole("SELLER");
        seller.setFullName("Dashboard Seller");
        return seller;
    }

    private static Admin admin() {
        Admin admin = new Admin("DASH-ADMIN", "dashboard-admin", "hash", "admin@test.local");
        admin.setRole("ADMIN");
        admin.setFullName("Dashboard Admin");
        return admin;
    }

    private static Bidder registeredBidder(String label) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = testUsername(label, suffix);
        Bidder bidder = (Bidder) AuthenticationService.getInstance().registerManualBidder(
                username,
                "secret",
                username + "@test.local",
                "Dashboard Snapshot"
        );
        bidder.setBalance(500.0);
        AuthenticationService.getInstance().updateUser(bidder);
        return bidder;
    }

    private static User registeredAdmin(String label) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = testUsername(label, suffix);
        User admin = AuthenticationService.getInstance().registerManualSeller(
                username,
                "secret",
                username + "@test.local",
                "Dashboard Admin Snapshot"
        );
        AuthenticationService.getInstance().updateUserRole(admin.getId(), "ADMIN");
        admin.setRole("ADMIN");
        return admin;
    }

    private static String testUsername(String label, String suffix) {
        String compactLabel = label.toLowerCase().replaceAll("[^a-z0-9]+", "");
        if (compactLabel.length() > 16) {
            compactLabel = compactLabel.substring(0, 16);
        }
        return compactLabel + "_" + suffix;
    }

    private static Seller registeredSeller(String label) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = testUsername(label, suffix);
        return (Seller) AuthenticationService.getInstance().registerManualSeller(
                username,
                "secret",
                username + "@test.local",
                "Dashboard Modal Seller"
        );
    }

    private static void answerWalletPinThenInfo() {
        JavaFxTestSupport.answerNextPasswordDialogThenCloseAlert(PIN, false);
    }

    private static void answerWalletPinOnly() {
        JavaFxTestSupport.answerNextPasswordDialog(PIN, false, ButtonType.OK);
    }

    private void trustWallet(User user) {
        var authorization = dashboardService.authorizeWallet(user, PIN, Duration.ofMinutes(5));
        session.trustWalletAuthorization(user.getId(), authorization.token(), authorization.expiresAt());
    }

    private static void closeInfoDialog() {
        JavaFxTestSupport.closeNextDialog(ButtonType.OK);
    }

    private static void waitForFxCondition(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            java.util.concurrent.atomic.AtomicBoolean satisfied = new java.util.concurrent.atomic.AtomicBoolean(false);
            JavaFxTestSupport.runAndWait(() -> satisfied.set(condition.getAsBoolean()));
            if (satisfied.get()) {
                return;
            }
            Thread.sleep(25L);
        }
        JavaFxTestSupport.runAndWait(() -> assertTrue(condition.getAsBoolean()));
    }

    private static boolean listViewHasItems(Object target, String fieldName) {
        try {
            return !field(target, fieldName, ListView.class).getItems().isEmpty();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static AuctionEligibilityEntry auctionEntry(
            String id,
            String name,
            String status,
            double currentPrice,
            double minimumBid,
            boolean eligible,
            boolean depositConfirmed,
            long remainingSeconds
    ) {
        return new AuctionEligibilityEntry(
                id,
                name,
                status,
                currentPrice,
                minimumBid,
                25.0,
                800.0,
                eligible,
                depositConfirmed,
                "27/05/2026 12:00",
                remainingSeconds
        );
    }

    private static WalletLinkedAccount walletAccount(String id, String userId, boolean primary, double balance) {
        return new WalletLinkedAccount(
                id,
                userId,
                primary ? "Primary Checking" : "Savings",
                "Test Bank",
                primary ? "1111222233334444" : "5555666677778888",
                balance,
                primary,
                LocalDateTime.of(2026, 5, 27, 9, 0)
        );
    }

    private static WalletTransaction walletTransaction(String id, String userId, double amount, double balanceAfter) {
        return new WalletTransaction(
                id,
                userId,
                "WALLET_TOP_UP",
                amount,
                balanceAfter - amount,
                balanceAfter,
                "REF-" + id,
                "Top up",
                LocalDateTime.of(2026, 5, 27, 9, 30)
        );
    }

    private static Item item(String id, String sellerId) {
        Item item = ItemFactory.createItem(
                "electronics",
                id,
                "Dashboard Camera",
                "Mirrorless camera",
                100.0,
                LocalDateTime.of(2026, 5, 27, 9, 0),
                LocalDateTime.of(2026, 5, 27, 10, 0),
                "Brand",
                24
        );
        item.setSellerId(sellerId);
        item.setApprovalStatus(ApprovalStatus.PENDING);
        return item;
    }

    private static AuctionSettlement settlement() {
        AuctionSettlement settlement = new AuctionSettlement(
                "SELLER-ITEM-1",
                "Dashboard Camera",
                "DASH-SELLER",
                "DASH-BIDDER",
                200.0,
                25.0,
                10.0,
                210.0,
                185.0,
                20.0,
                180.0,
                LocalDateTime.of(2026, 5, 27, 10, 0)
        );
        settlement.setStatus(AuctionSettlementStatus.AWAITING_SELLER_CONFIRMATION);
        return settlement;
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private static <T> T field(Object target, String name, Class<T> type) throws Exception {
        return type.cast(field(target, name));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object invoke(Object target, String name, Object... args) throws Exception {
        Class<?>[] parameterTypes = new Class<?>[args.length];
        for (int index = 0; index < args.length; index++) {
            parameterTypes[index] = args[index] == null ? Object.class : primitiveAwareType(args[index].getClass());
        }
        Method method = findMethod(target.getClass(), name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static Object invokeOnFx(Object target, String name, Object... args) {
        java.util.concurrent.atomic.AtomicReference<Object> result = new java.util.concurrent.atomic.AtomicReference<>();
        JavaFxTestSupport.runAndWait(() -> {
            try {
                result.set(invoke(target, name, args));
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
        return result.get();
    }

    private static void invokeWithClosedDialog(Object target, String name) throws Exception {
        JavaFxTestSupport.closeNextDialog(ButtonType.OK);
        invokeOnFx(target, name);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>[] argumentTypes) throws NoSuchMethodException {
        for (Method method : type.getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != argumentTypes.length) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean compatible = true;
            for (int index = 0; index < parameterTypes.length; index++) {
                if (argumentTypes[index] == Object.class && !parameterTypes[index].isPrimitive()) {
                    continue;
                }
                if (!wrap(parameterTypes[index]).isAssignableFrom(wrap(argumentTypes[index]))) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                return method;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static Class<?> primitiveAwareType(Class<?> type) {
        if (type == Boolean.class) {
            return boolean.class;
        }
        if (type == Long.class) {
            return long.class;
        }
        if (type == Double.class) {
            return double.class;
        }
        if (type == Integer.class) {
            return int.class;
        }
        return type;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        return type;
    }

    private static Object newNested(String className, Class<?>[] parameterTypes, Object... args) throws Exception {
        Class<?> type = Class.forName(className);
        Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }

    private static Object invokeStatic(String className, String name) throws Exception {
        return invokeStatic(className, name, new Class<?>[0]);
    }

    private static Object invokeStatic(String className, String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Class<?> type = Class.forName(className);
        Method method = type.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static void restoreProperty(String propertyName, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, previousValue);
        }
    }

    private static AuctionApiClient newApiClient() throws Exception {
        Constructor<AuctionApiClient> constructor = AuctionApiClient.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
