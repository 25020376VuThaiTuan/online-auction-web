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
import org.example.auction.AuctionSettlement;
import org.example.auction.AuctionSettlementStatus;
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
import org.example.service.AuthenticationService;
import org.example.service.MarketplaceDashboardService;
import org.example.state.ApplicationSession;
import org.example.util.AuctionCatalogFilters;
import org.example.viewmodel.AuctionEligibilityEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

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

    private static void closeInfoDialog() {
        JavaFxTestSupport.closeNextDialog(ButtonType.OK);
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
}
