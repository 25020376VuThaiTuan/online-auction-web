package org.example.controller;

import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.example.auction.AuctionSettlement;
import org.example.auction.AuctionSettlementStatus;
import org.example.model.Bid;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.model.ItemFactory;
import org.example.model.Seller;
import org.example.model.User;
import org.example.model.WalletTransaction;
import org.example.state.ApplicationSession;
import org.example.util.AuctionCatalogFilters;
import org.example.viewmodel.AuctionListEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerCoverageExpansionTest {
    private final ApplicationSession session = ApplicationSession.getInstance();

    @BeforeAll
    static void initToolkit() {
        JavaFxTestSupport.startToolkit();
    }

    @BeforeEach
    void loginUser() {
        Bidder bidder = new Bidder("BIDDER-CONTROLLER", "controller-bidder", "hash", "controller@test.local", 500.0);
        bidder.setFullName("Controller Bidder");
        session.login(bidder);
    }

    @AfterEach
    void clearSession() {
        session.logout();
    }

    @Test
    void auctionListFiltersWatchActionsAndResultLabelsUpdateTableState() throws Exception {
        AuctionListController controller = auctionListController();
        List<AuctionListEntry> entries = List.of(
                new AuctionListEntry("A-1", "Vintage Camera", "RUNNING", 200.0, 210.0, "27/05/2026 10:00", 60L),
                new AuctionListEntry("A-2", "Studio Lamp", "FINISHED", 50.0, 60.0, "27/05/2026 09:00", 0L),
                new AuctionListEntry("A-3", "Camera Lens", "OPEN", 100.0, 110.0, "27/05/2026 11:00", 120L)
        );

        invoke(controller, "configureMarketplaceControls");
        setField(controller, "latestEntries", entries);
        invoke(controller, "applyAuctionFilters");

        TableView<AuctionListEntry> table = field(controller, "auctionTable");
        Label resultCount = field(controller, "resultCountLabel");
        Button openButton = field(controller, "openAuctionButton");
        Button toggleButton = field(controller, "toggleWatchButton");

        assertEquals(List.of("A-2", "A-1", "A-3"), table.getItems().stream().map(AuctionListEntry::getItemId).toList());
        assertEquals("3 auctions available", resultCount.getText());
        assertTrue(openButton.isDisabled());
        assertTrue(toggleButton.isDisabled());

        table.getSelectionModel().select(1);
        invoke(controller, "updateAuctionSelectionActions");
        assertFalse(openButton.isDisabled());
        assertEquals("Watch", toggleButton.getText());

        invoke(controller, "handleToggleWatch");
        assertTrue(session.isAuctionWatched("A-1"));
        assertEquals("Unwatch", toggleButton.getText());

        @SuppressWarnings("unchecked")
        ChoiceBox<String> sortChoice = field(controller, "sortChoiceBox");
        sortChoice.setValue(AuctionCatalogFilters.SORT_PRICE_HIGH);
        invoke(controller, "applyAuctionFilters");
        assertEquals(List.of("A-1", "A-3", "A-2"), table.getItems().stream().map(AuctionListEntry::getItemId).toList());

        CheckBox watchedOnly = field(controller, "watchedOnlyCheckBox");
        watchedOnly.setSelected(true);
        invoke(controller, "applyAuctionFilters");
        assertEquals(List.of("A-1"), table.getItems().stream().map(AuctionListEntry::getItemId).toList());

        invoke(controller, "handleWatchVisible");
        assertTrue(session.isAuctionWatched("A-1"));

        invoke(controller, "handleClearWatched");
        assertFalse(session.isAuctionWatched("A-1"));
        assertEquals("No watched auctions match the current filters.", resultCount.getText());

        invoke(controller, "handleClearFilters");
        assertFalse(watchedOnly.isSelected());
        assertEquals("3 auctions available", resultCount.getText());
    }

    @Test
    void auctionListSnapshotRefreshFailureAndTokenHelpersCoverNonUiBranches() throws Exception {
        AuctionListController controller = auctionListController();
        invoke(controller, "configureMarketplaceControls");

        Object snapshot = newNested(
                "org.example.controller.AuctionListController$AuctionListSnapshot",
                new Class<?>[]{String.class, List.class},
                "Signed in as test",
                List.of(new AuctionListEntry("A-1", "Camera", "RUNNING", 10.0, 20.0, "N/A", 1L))
        );
        invoke(controller, "applyAuctionListSnapshot", snapshot);

        Label welcome = field(controller, "welcomeLabel");
        TableView<AuctionListEntry> table = field(controller, "auctionTable");
        assertEquals("Signed in as: Signed in as test", welcome.getText());
        assertEquals(1, table.getItems().size());

        assertEquals("boom", invoke(controller, "refreshFailureMessage", new CompletionException(new IllegalStateException("boom"))));
        assertEquals("Auction data could not be refreshed.", invoke(controller, "refreshFailureMessage", new RuntimeException(" ")));
        assertEquals("deep", invoke(controller, "refreshFailureMessage", new ExecutionException(new IllegalArgumentException("deep"))));
        assertFalse((boolean) invoke(controller, "useApi"));
        Exception exception = assertThrows(Exception.class, () -> invoke(controller, "apiToken"));
        assertTrue(exception.getCause() instanceof IllegalStateException);
    }

    @Test
    void auctionControllerAppliesSnapshotBidHistoryAndClockState() throws Exception {
        AuctionController controller = auctionController();
        LocalDateTime bidTime = LocalDateTime.of(2026, 5, 27, 10, 15, 30);
        Bid bid = new Bid("BID-1", "BIDDER-CONTROLLER", "ITEM-1", 150.0, bidTime);
        Object snapshot = newNested(
                "org.example.controller.AuctionController$AuctionViewSnapshot",
                new Class<?>[]{
                        boolean.class, String.class, String.class, String.class, double.class, double.class,
                        String.class, long.class, List.class, double.class, boolean.class, boolean.class,
                        boolean.class, String.class, boolean.class, boolean.class
                },
                false,
                "Vintage Camera",
                "Mirrorless",
                "RUNNING",
                140.0,
                150.0,
                "27/05/2026 11:00",
                90L,
                List.of(bid),
                30.0,
                true,
                true,
                false,
                "Settlement: pending",
                false,
                true
        );

        setField(controller, "selectedAuctionId", "ITEM-1");
        invoke(controller, "applyAuctionViewSnapshot", snapshot);

        Label name = field(controller, "itemNameLabel");
        Label currentPrice = field(controller, "currentPriceLabel");
        Label winner = field(controller, "currentWinnerLabel");
        ComboBox<String> bidAmount = field(controller, "bidAmountCombo");
        Button placeBid = field(controller, "placeBidButton");
        Button admit = field(controller, "admitResultButton");
        Button confirm = field(controller, "confirmReceivedButton");
        TableView<Bid> bidTable = field(controller, "bidTable");

        assertEquals("Vintage Camera", name.getText());
        assertEquals("$140.00", currentPrice.getText());
        assertTrue(winner.getText().contains("Controller Bidder"));
        assertEquals(1, bidTable.getItems().size());
        assertFalse(bidAmount.isDisabled());
        assertFalse(placeBid.isDisabled());
        assertFalse(admit.isDisabled());
        assertTrue(confirm.isDisabled());

        setField(controller, "lastSnapshotAppliedAtMillis", System.currentTimeMillis() - 5_000L);
        long remaining = (long) invoke(controller, "currentSecondsRemaining", snapshot);
        assertTrue(remaining <= 90L && remaining >= 80L);

        setField(controller, "lastSnapshotAppliedAtMillis", 0L);
        assertEquals(90L, invoke(controller, "currentSecondsRemaining", snapshot));

        invoke(controller, "updateClockOnly");
        Label timeRemaining = field(controller, "timeRemainingLabel");
        assertFalse(timeRemaining.getText().isBlank());
    }

    @Test
    void auctionControllerSettlementAndFormattingBranchesReturnExpectedState() throws Exception {
        AuctionController controller = auctionController();
        AuctionSettlement local = settlement(AuctionSettlementStatus.AWAITING_WINNER_ADMISSION);
        Object localState = invoke(controller, "localSettlementState", local, "BIDDER-CONTROLLER");
        assertEquals("Settlement: " + local.getDisplaySummary(), invoke(localState, "summary"));
        assertEquals(false, invoke(localState, "admitDisabled"));
        assertEquals(true, invoke(localState, "confirmDisabled"));

        local.setStatus(AuctionSettlementStatus.AWAITING_BUYER_CONFIRMATION);
        Object confirmState = invoke(controller, "localSettlementState", local, "BIDDER-CONTROLLER");
        assertEquals(true, invoke(confirmState, "admitDisabled"));
        assertEquals(false, invoke(confirmState, "confirmDisabled"));

        Object missingState = invoke(controller, "localSettlementState", null, "BIDDER-CONTROLLER");
        assertEquals("Settlement: N/A", invoke(missingState, "summary"));

        assertTrue((boolean) invoke(controller, "isFinishedStatus", "paid"));
        assertTrue((boolean) invoke(controller, "isFinishedStatus", "cancelled"));
        assertFalse((boolean) invoke(controller, "isFinishedStatus", "running"));
        assertEquals("", invoke(controller, "selectedBidAmountText"));

        ComboBox<String> bidAmount = field(controller, "bidAmountCombo");
        bidAmount.setValue(" 123.45 ");
        assertEquals("123.45", invoke(controller, "selectedBidAmountText"));
        bidAmount.setEditable(true);
        bidAmount.getEditor().setText(" 200.00 ");
        assertEquals("200.00", invoke(controller, "selectedBidAmountText"));

        assertEquals("N/A", invoke(controller, "formatBidNotificationTime", (Object) null));
        assertEquals("Auction details could not be refreshed.", invoke(controller, "refreshFailureMessage", new RuntimeException(" ")));
    }

    @Test
    void dashboardPresentersApplyClearFailureAndBidHistoryStates() {
        TableView<User> users = new TableView<>();
        TableView<Item> pending = new TableView<>();
        ListView<String> settlementLines = new ListView<>();
        ListView<String> walletAudit = new ListView<>();
        List<AuctionSettlement> settlementItems = new ArrayList<>();
        DashboardAdminPresenter admin = new DashboardAdminPresenter(users, pending, settlementLines, walletAudit, settlementItems);
        Seller seller = new Seller("SELLER-1", "seller", "hash", "seller@test.local");
        Item item = item("ITEM-1");
        AuctionSettlement settlement = settlement(AuctionSettlementStatus.AWAITING_SELLER_CONFIRMATION);

        admin.apply(true, true, null, List.of(seller), List.of(item), List.of("line"), List.of(settlement));
        assertEquals(1, users.getItems().size());
        assertEquals(1, pending.getItems().size());
        assertEquals(List.of("line"), settlementLines.getItems());
        assertEquals(1, settlementItems.size());

        admin.handleRefreshFailure("offline");
        assertEquals(List.of("Admin data unavailable."), settlementLines.getItems());
        assertEquals(List.of("offline"), walletAudit.getItems());

        admin.apply(false, true, null, List.of(seller), List.of(item), List.of("line"), List.of(settlement));
        assertTrue(users.getItems().isEmpty());
        assertTrue(pending.getItems().isEmpty());
        assertTrue(settlementItems.isEmpty());

        Label winner = new Label();
        ListView<String> bidNotifications = new ListView<>();
        DashboardBidPanelPresenter bidPanel = new DashboardBidPanelPresenter(winner, bidNotifications, null);
        LocalDateTime bidTime = LocalDateTime.of(2026, 5, 27, 10, 0);
        List<Bid> bids = List.of(
                new Bid("BID-1", "BIDDER-1", "ITEM-1", 120.0, bidTime),
                new Bid("BID-2", "BIDDER-2", "ITEM-1", 130.0, bidTime.plusMinutes(1))
        );

        bidPanel.applyBidHistory(bids, bidderId -> "Name " + bidderId);
        assertTrue(winner.getText().contains("Name BIDDER-2"));
        assertEquals(2, bidNotifications.getItems().size());

        bidPanel.addBidActivityNotification("Alice", 140.0, bidTime.plusMinutes(2), "accepted");
        assertTrue(bidNotifications.getItems().getFirst().contains("Alice"));
        assertTrue(winner.getText().contains("$140.00"));

        bidPanel.addMessage("Alice", "message");
        assertTrue(bidNotifications.getItems().getFirst().contains("message"));

        bidPanel.clearStatusViews();
        assertEquals("Current winner: N/A", winner.getText());
        assertTrue(bidNotifications.getItems().isEmpty());
    }

    @Test
    void dashboardPrivateRecordsNormalizeSafeValuesAndFactoryStates() throws Exception {
        Object notification = newNested(
                "org.example.controller.DashboardController$DashboardNotification",
                new Class<?>[]{String.class, String.class, String.class, String.class},
                " ",
                " Payment failed ",
                " body ",
                " "
        );
        assertEquals("Payment failed: body", invoke(notification, "displayText"));
        assertEquals("Payment failed|body|Payment failed: body", invoke(notification, "popupKey"));
        assertTrue((boolean) invoke(notification, "isWarning"));

        Object notAdmin = invokeStatic("org.example.controller.DashboardController$AdminSectionSnapshot", "notAdmin");
        assertEquals(false, invoke(notAdmin, "admin"));

        Object failedAdmin = invokeStatic(
                "org.example.controller.DashboardController$AdminSectionSnapshot",
                "failure",
                new Class<?>[]{String.class},
                "offline"
        );
        assertEquals(true, invoke(failedAdmin, "admin"));
        assertEquals("offline", invoke(failedAdmin, "failureMessage"));

        Object disabled = invokeStatic("org.example.controller.DashboardController$SettlementButtonState", "disabled");
        assertEquals(true, invoke(disabled, "admitDisabled"));
        assertEquals(true, invoke(disabled, "confirmDisabled"));
    }

    private AuctionListController auctionListController() throws Exception {
        AuctionListController controller = new AuctionListController();
        setField(controller, "welcomeLabel", new Label());
        setField(controller, "auctionTable", new TableView<AuctionListEntry>());
        setField(controller, "watchColumn", new TableColumn<AuctionListEntry, String>());
        setField(controller, "nameColumn", new TableColumn<AuctionListEntry, String>());
        setField(controller, "statusColumn", new TableColumn<AuctionListEntry, String>());
        setField(controller, "currentPriceColumn", new TableColumn<AuctionListEntry, Double>());
        setField(controller, "minimumBidColumn", new TableColumn<AuctionListEntry, Double>());
        setField(controller, "timeRemainingColumn", new TableColumn<AuctionListEntry, String>());
        setField(controller, "endTimeColumn", new TableColumn<AuctionListEntry, String>());
        setField(controller, "searchField", new TextField());
        setField(controller, "statusFilterChoiceBox", new ChoiceBox<String>());
        setField(controller, "sortChoiceBox", new ChoiceBox<String>());
        setField(controller, "openOnlyCheckBox", new CheckBox());
        setField(controller, "watchedOnlyCheckBox", new CheckBox());
        setField(controller, "resultCountLabel", new Label());
        setField(controller, "toggleWatchButton", new Button());
        setField(controller, "openAuctionButton", new Button());
        return controller;
    }

    private AuctionController auctionController() throws Exception {
        AuctionController controller = new AuctionController();
        setField(controller, "userLabel", new Label());
        setField(controller, "itemNameLabel", new Label());
        setField(controller, "descriptionLabel", new Label());
        setField(controller, "statusLabel", new Label());
        setField(controller, "currentPriceLabel", new Label());
        setField(controller, "minimumBidLabel", new Label());
        setField(controller, "endTimeLabel", new Label());
        setField(controller, "timeRemainingLabel", new Label());
        setField(controller, "bidEntryTimeRemainingLabel", new Label());
        setField(controller, "depositLabel", new Label());
        setField(controller, "settlementLabel", new Label());
        setField(controller, "currentWinnerLabel", new Label());
        setField(controller, "bidNotificationList", new ListView<String>());
        setField(controller, "bidTable", new TableView<Bid>());
        setField(controller, "bidderColumn", new TableColumn<Bid, String>());
        setField(controller, "amountColumn", new TableColumn<Bid, Double>());
        setField(controller, "timeColumn", new TableColumn<Bid, LocalDateTime>());
        ComboBox<String> bidAmount = new ComboBox<>();
        bidAmount.setEditable(true);
        setField(controller, "bidAmountCombo", bidAmount);
        setField(controller, "placeBidButton", new Button());
        setField(controller, "confirmEntryButton", new Button());
        setField(controller, "admitResultButton", new Button());
        setField(controller, "confirmReceivedButton", new Button());
        setField(controller, "refreshActive", true);
        return controller;
    }

    private static AuctionSettlement settlement(AuctionSettlementStatus status) {
        AuctionSettlement settlement = new AuctionSettlement(
                "ITEM-1",
                "Camera",
                "SELLER-1",
                "BIDDER-CONTROLLER",
                150.0,
                30.0,
                7.5,
                157.5,
                127.5,
                15.0,
                135.0,
                LocalDateTime.of(2026, 5, 27, 10, 0)
        );
        settlement.setStatus(status);
        return settlement;
    }

    private static Item item(String id) {
        return ItemFactory.createItem(
                "electronics",
                id,
                "Camera",
                "Mirrorless",
                100.0,
                LocalDateTime.of(2026, 5, 27, 9, 0),
                LocalDateTime.of(2026, 5, 27, 10, 0),
                "Brand",
                12
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
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
