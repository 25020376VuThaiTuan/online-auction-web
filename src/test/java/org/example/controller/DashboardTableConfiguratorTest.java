package org.example.controller;

import javafx.collections.FXCollections;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.example.model.ApprovalStatus;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.model.ItemFactory;
import org.example.model.User;
import org.example.model.WalletLinkedAccount;
import org.example.model.WalletTransaction;
import org.example.state.ApplicationSession;
import org.example.viewmodel.AuctionEligibilityEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class DashboardTableConfiguratorTest {
    private final ApplicationSession session = ApplicationSession.getInstance();

    @BeforeAll
    static void initToolkit() {
        JavaFxTestSupport.startToolkit();
    }

    @AfterEach
    void tearDown() {
        session.logout();
        session.clearWatchedAuctions();
    }

    @Test
    void walletTablesExposeFormattedTransactionAndAccountValues() {
        TableView<WalletTransaction> transactionTable = new TableView<>();
        TableColumn<WalletTransaction, String> timeColumn = new TableColumn<>();
        TableColumn<WalletTransaction, String> typeColumn = new TableColumn<>();
        TableColumn<WalletTransaction, String> amountColumn = new TableColumn<>();
        TableColumn<WalletTransaction, String> balanceColumn = new TableColumn<>();
        TableColumn<WalletTransaction, String> noteColumn = new TableColumn<>();
        transactionTable.getColumns().add(timeColumn);
        transactionTable.getColumns().add(typeColumn);
        transactionTable.getColumns().add(amountColumn);
        transactionTable.getColumns().add(balanceColumn);
        transactionTable.getColumns().add(noteColumn);

        TableView<WalletLinkedAccount> accountTable = new TableView<>();
        TableColumn<WalletLinkedAccount, String> primaryColumn = new TableColumn<>();
        TableColumn<WalletLinkedAccount, String> providerColumn = new TableColumn<>();
        TableColumn<WalletLinkedAccount, String> nameColumn = new TableColumn<>();
        TableColumn<WalletLinkedAccount, String> referenceColumn = new TableColumn<>();
        TableColumn<WalletLinkedAccount, String> accountBalanceColumn = new TableColumn<>();
        accountTable.getColumns().add(primaryColumn);
        accountTable.getColumns().add(providerColumn);
        accountTable.getColumns().add(nameColumn);
        accountTable.getColumns().add(referenceColumn);
        accountTable.getColumns().add(accountBalanceColumn);

        DashboardTableConfigurator.configureWalletTables(new DashboardTableConfigurator.WalletTables(
                transactionTable,
                timeColumn,
                typeColumn,
                amountColumn,
                balanceColumn,
                noteColumn,
                accountTable,
                primaryColumn,
                providerColumn,
                nameColumn,
                referenceColumn,
                accountBalanceColumn
        ));

        WalletTransaction transaction = new WalletTransaction(
                "TX-1",
                "USER-1",
                "LOCK_DEPOSIT",
                25.0,
                100.0,
                75.0,
                "ITEM-1",
                "Entry deposit",
                LocalDateTime.of(2026, 5, 27, 10, 30)
        );
        WalletLinkedAccount account = new WalletLinkedAccount(
                "ACC-1",
                "USER-1",
                "Savings",
                "Demo Bank",
                "1234567890",
                125.5,
                true,
                LocalDateTime.of(2026, 5, 27, 9, 0)
        );

        assertEquals("27/05/2026 10:30", timeColumn.getCellObservableValue(transaction).getValue());
        assertEquals("LOCK DEPOSIT", typeColumn.getCellObservableValue(transaction).getValue());
        assertEquals("$25.00", amountColumn.getCellObservableValue(transaction).getValue());
        assertEquals("$75.00", balanceColumn.getCellObservableValue(transaction).getValue());
        assertEquals("Entry deposit", noteColumn.getCellObservableValue(transaction).getValue());
        assertInstanceOf(Label.class, transactionTable.getPlaceholder());

        assertEquals("Yes", primaryColumn.getCellObservableValue(account).getValue());
        assertEquals("Demo Bank", providerColumn.getCellObservableValue(account).getValue());
        assertEquals("Savings", nameColumn.getCellObservableValue(account).getValue());
        assertEquals("****7890", referenceColumn.getCellObservableValue(account).getValue());
        assertEquals("$125.50", accountBalanceColumn.getCellObservableValue(account).getValue());
        assertInstanceOf(Label.class, accountTable.getPlaceholder());
    }

    @Test
    void auctionTableColumnsAndSelectionCallbacksReflectCurrentEntry() throws Exception {
        session.watchAuction("ITEM-1");
        TableView<AuctionEligibilityEntry> table = new TableView<>();
        TableColumn<AuctionEligibilityEntry, String> watchColumn = new TableColumn<>();
        TableColumn<AuctionEligibilityEntry, String> nameColumn = new TableColumn<>();
        TableColumn<AuctionEligibilityEntry, String> statusColumn = new TableColumn<>();
        TableColumn<AuctionEligibilityEntry, Double> currentPriceColumn = new TableColumn<>();
        TableColumn<AuctionEligibilityEntry, Double> minimumBidColumn = new TableColumn<>();
        TableColumn<AuctionEligibilityEntry, Double> requiredDepositColumn = new TableColumn<>();
        TableColumn<AuctionEligibilityEntry, Double> availableBalanceColumn = new TableColumn<>();
        TableColumn<AuctionEligibilityEntry, String> timeRemainingColumn = new TableColumn<>();
        TableColumn<AuctionEligibilityEntry, String> endTimeColumn = new TableColumn<>();
        TableColumn<AuctionEligibilityEntry, String> eligibleColumn = new TableColumn<>();
        table.getColumns().add(watchColumn);
        table.getColumns().add(nameColumn);
        table.getColumns().add(statusColumn);
        table.getColumns().add(currentPriceColumn);
        table.getColumns().add(minimumBidColumn);
        table.getColumns().add(requiredDepositColumn);
        table.getColumns().add(availableBalanceColumn);
        table.getColumns().add(timeRemainingColumn);
        table.getColumns().add(endTimeColumn);
        table.getColumns().add(eligibleColumn);

        AtomicReference<String> selectedId = new AtomicReference<>("");
        AtomicReference<AuctionEligibilityEntry> watchEntry = new AtomicReference<>();
        AtomicReference<AuctionEligibilityEntry> summaryEntry = new AtomicReference<>();
        AtomicReference<Boolean> summarySelectionChanged = new AtomicReference<>();
        AtomicReference<AuctionEligibilityEntry> refreshedEntry = new AtomicReference<>();
        AtomicReference<AuctionEligibilityEntry> openedBiddingEntry = new AtomicReference<>();
        AtomicInteger clearCount = new AtomicInteger();

        DashboardTableConfigurator.configureAuctionTable(new DashboardTableConfigurator.AuctionTableConfig(
                session,
                table,
                watchColumn,
                nameColumn,
                statusColumn,
                currentPriceColumn,
                minimumBidColumn,
                requiredDepositColumn,
                availableBalanceColumn,
                timeRemainingColumn,
                endTimeColumn,
                eligibleColumn,
                () -> false,
                watchEntry::set,
                clearCount::incrementAndGet,
                (entry, selectionChanged) -> {
                    summaryEntry.set(entry);
                    summarySelectionChanged.set(selectionChanged);
                },
                refreshedEntry::set,
                selectedId::set,
                selectedId::get,
                openedBiddingEntry::set,
                value -> "style-" + value.toLowerCase().replace(' ', '-')
        ));

        AuctionEligibilityEntry entry = new AuctionEligibilityEntry(
                "ITEM-1",
                "Vintage Camera",
                "RUNNING",
                100.0,
                110.0,
                25.0,
                300.0,
                true,
                true,
                "27/05/2026 11:00",
                90
        );
        table.setItems(FXCollections.observableArrayList(entry));
        table.getSelectionModel().select(entry);

        assertEquals("Watching", watchColumn.getCellObservableValue(entry).getValue());
        assertEquals("Vintage Camera", nameColumn.getCellObservableValue(entry).getValue());
        assertEquals("RUNNING", statusColumn.getCellObservableValue(entry).getValue());
        assertEquals(100.0, currentPriceColumn.getCellObservableValue(entry).getValue());
        assertEquals(110.0, minimumBidColumn.getCellObservableValue(entry).getValue());
        assertEquals(25.0, requiredDepositColumn.getCellObservableValue(entry).getValue());
        assertEquals(300.0, availableBalanceColumn.getCellObservableValue(entry).getValue());
        assertEquals("1m 30s", timeRemainingColumn.getCellObservableValue(entry).getValue());
        assertEquals("27/05/2026 11:00", endTimeColumn.getCellObservableValue(entry).getValue());
        assertEquals("Entered", eligibleColumn.getCellObservableValue(entry).getValue());
        assertEquals("ITEM-1", selectedId.get());
        assertSame(entry, watchEntry.get());
        assertSame(entry, summaryEntry.get());
        assertEquals(Boolean.TRUE, summarySelectionChanged.get());
        assertSame(entry, refreshedEntry.get());

        TableCell<AuctionEligibilityEntry, String> watchCell = watchColumn.getCellFactory().call(watchColumn);
        invokeUpdateItem(watchCell, "Watching", false);
        assertEquals("Watching", watchCell.getText());
        invokeUpdateItem(watchCell, "", false);
        assertEquals(null, watchCell.getText());

        TableCell<AuctionEligibilityEntry, String> statusCell = statusColumn.getCellFactory().call(statusColumn);
        invokeUpdateItem(statusCell, "RUNNING", false);
        assertEquals("RUNNING", statusCell.getText());
        invokeUpdateItem(statusCell, null, false);
        assertEquals(null, statusCell.getText());

        TableCell<AuctionEligibilityEntry, String> eligibleCell = eligibleColumn.getCellFactory().call(eligibleColumn);
        invokeUpdateItem(eligibleCell, "Can Enter", false);
        assertEquals("Can Enter", eligibleCell.getText());
        invokeUpdateItem(eligibleCell, null, false);
        assertEquals(null, eligibleCell.getText());

        TableRow<AuctionEligibilityEntry> row = table.getRowFactory().call(table);
        invokeUpdateItem(row, entry, false);
        row.getOnMouseClicked().handle(new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                0.0,
                0.0,
                0.0,
                0.0,
                MouseButton.PRIMARY,
                2,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                null
        ));
        assertSame(entry, openedBiddingEntry.get());
        invokeUpdateItem(row, null, true);

        table.getSelectionModel().clearSelection();

        assertEquals(1, clearCount.get());
        assertEquals(null, watchEntry.get());
    }

    @Test
    void sellerAndAdminTablesExposeColumnsChoicesAndSelectionCallbacks() {
        Item sellerItem = item("ITEM-1", "SELLER-1", ApprovalStatus.PENDING);
        TableView<Item> sellerTable = new TableView<>();
        TableColumn<Item, String> itemNameColumn = new TableColumn<>();
        TableColumn<Item, ApprovalStatus> sellerStatusColumn = new TableColumn<>();
        TableColumn<Item, Double> sellerPriceColumn = new TableColumn<>();
        TableColumn<Item, String> startColumn = new TableColumn<>();
        TableColumn<Item, String> endColumn = new TableColumn<>();
        sellerTable.getColumns().add(itemNameColumn);
        sellerTable.getColumns().add(sellerStatusColumn);
        sellerTable.getColumns().add(sellerPriceColumn);
        sellerTable.getColumns().add(startColumn);
        sellerTable.getColumns().add(endColumn);
        ListView<String> bidHistoryList = new ListView<>();
        bidHistoryList.setItems(FXCollections.observableArrayList("old bid"));
        AtomicReference<Item> selectedSellerItem = new AtomicReference<>();
        AtomicReference<Item> refreshedSellerItem = new AtomicReference<>();
        AtomicInteger clearedSelections = new AtomicInteger();

        DashboardTableConfigurator.configureSellerTable(new DashboardTableConfigurator.SellerTableConfig(
                sellerTable,
                itemNameColumn,
                sellerStatusColumn,
                sellerPriceColumn,
                startColumn,
                endColumn,
                bidHistoryList,
                () -> false,
                (item, fromApiRefresh) -> {
                    selectedSellerItem.set(item);
                    if (item == null) {
                        clearedSelections.incrementAndGet();
                    }
                },
                refreshedSellerItem::set
        ));
        sellerTable.setItems(FXCollections.observableArrayList(sellerItem));
        sellerTable.getSelectionModel().select(sellerItem);

        assertEquals("Vintage Camera", itemNameColumn.getCellObservableValue(sellerItem).getValue());
        assertEquals(ApprovalStatus.PENDING, sellerStatusColumn.getCellObservableValue(sellerItem).getValue());
        assertEquals(100.0, sellerPriceColumn.getCellObservableValue(sellerItem).getValue());
        assertEquals("27/05/2026 09:00", startColumn.getCellObservableValue(sellerItem).getValue());
        assertEquals("27/05/2026 11:00", endColumn.getCellObservableValue(sellerItem).getValue());
        assertSame(sellerItem, selectedSellerItem.get());
        assertSame(sellerItem, refreshedSellerItem.get());

        sellerTable.getSelectionModel().clearSelection();

        assertEquals(0, bidHistoryList.getItems().size());
        assertEquals(1, clearedSelections.get());

        TableView<User> userTable = new TableView<>();
        TableColumn<User, String> usernameColumn = new TableColumn<>();
        TableColumn<User, String> fullNameColumn = new TableColumn<>();
        TableColumn<User, String> emailColumn = new TableColumn<>();
        TableColumn<User, String> roleColumn = new TableColumn<>();
        userTable.getColumns().add(usernameColumn);
        userTable.getColumns().add(fullNameColumn);
        userTable.getColumns().add(emailColumn);
        userTable.getColumns().add(roleColumn);
        ChoiceBox<String> roleChoiceBox = new ChoiceBox<>();
        TableView<Item> pendingItemsTable = new TableView<>();
        TableColumn<Item, String> pendingItemNameColumn = new TableColumn<>();
        TableColumn<Item, String> pendingSellerColumn = new TableColumn<>();
        TableColumn<Item, ApprovalStatus> pendingStatusColumn = new TableColumn<>();
        pendingItemsTable.getColumns().add(pendingItemNameColumn);
        pendingItemsTable.getColumns().add(pendingSellerColumn);
        pendingItemsTable.getColumns().add(pendingStatusColumn);

        DashboardTableConfigurator.configureAdminTables(new DashboardTableConfigurator.AdminTableConfig(
                userTable,
                usernameColumn,
                fullNameColumn,
                emailColumn,
                roleColumn,
                roleChoiceBox,
                pendingItemsTable,
                pendingItemNameColumn,
                pendingSellerColumn,
                pendingStatusColumn
        ));

        Bidder user = new Bidder("USER-1", "bidder", "hash", "bidder@test.local", 100.0);
        user.setFullName("Bidder One");
        user.setRole("SELLER");
        assertEquals("bidder", usernameColumn.getCellObservableValue(user).getValue());
        assertEquals("Bidder One", fullNameColumn.getCellObservableValue(user).getValue());
        assertEquals("bidder@test.local", emailColumn.getCellObservableValue(user).getValue());
        assertEquals("SELLER", roleColumn.getCellObservableValue(user).getValue());
        assertEquals(FXCollections.observableArrayList("BIDDER", "SELLER", "ADMIN"), roleChoiceBox.getItems());
        assertEquals("Vintage Camera", pendingItemNameColumn.getCellObservableValue(sellerItem).getValue());
        assertEquals("SELLER-1", pendingSellerColumn.getCellObservableValue(sellerItem).getValue());
        assertEquals(ApprovalStatus.PENDING, pendingStatusColumn.getCellObservableValue(sellerItem).getValue());

        ChoiceBox<String> sellerItemTypeChoiceBox = new ChoiceBox<>();
        DashboardTableConfigurator.configureSellerItemTypeChoice(sellerItemTypeChoiceBox);
        assertEquals(FXCollections.observableArrayList("electronics", "art", "vehicle"), sellerItemTypeChoiceBox.getItems());
        assertEquals("electronics", sellerItemTypeChoiceBox.getValue());
    }

    private static Item item(String id, String sellerId, ApprovalStatus status) {
        Item item = ItemFactory.createItem(
                "art",
                id,
                "Vintage Camera",
                "Mirrorless",
                100.0,
                LocalDateTime.of(2026, 5, 27, 9, 0),
                LocalDateTime.of(2026, 5, 27, 11, 0),
                "Artist",
                2026
        );
        item.setSellerId(sellerId);
        item.setApprovalStatus(status);
        return item;
    }

    private static void invokeUpdateItem(Object target, Object value, boolean empty) throws Exception {
        Method method = updateItemMethod(target.getClass());
        method.setAccessible(true);
        method.invoke(target, value, empty);
    }

    private static Method updateItemMethod(Class<?> type) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if ("updateItem".equals(method.getName()) && method.getParameterCount() == 2) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchMethodException("updateItem");
    }
}
