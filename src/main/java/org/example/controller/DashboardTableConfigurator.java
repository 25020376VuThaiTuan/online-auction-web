package org.example.controller;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.example.model.ApprovalStatus;
import org.example.model.Item;
import org.example.model.User;
import org.example.model.WalletLinkedAccount;
import org.example.model.WalletTransaction;
import org.example.state.ApplicationSession;
import org.example.util.AuctionCatalogFilters;
import org.example.util.AuctionDisplayFormatter;
import org.example.util.ResponsiveViewSupport;
import org.example.viewmodel.AuctionEligibilityEntry;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

final class DashboardTableConfigurator {
    private DashboardTableConfigurator() {
    }

    static void configureWalletTables(WalletTables tables) {
        tables.transactionTimeColumn().setCellValueFactory(cellData ->
                new SimpleStringProperty(DashboardFormatters.formatDateTime(cellData.getValue().createdAt())));
        tables.transactionTypeColumn().setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().transactionType().replace('_', ' ')));
        tables.transactionAmountColumn().setCellValueFactory(cellData ->
                new SimpleStringProperty(AuctionDisplayFormatter.formatCurrency(cellData.getValue().amount())));
        tables.transactionBalanceColumn().setCellValueFactory(cellData ->
                new SimpleStringProperty(AuctionDisplayFormatter.formatCurrency(cellData.getValue().balanceAfter())));
        tables.transactionNoteColumn().setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().note()));
        ResponsiveViewSupport.configureResponsiveTable(tables.transactionTable());

        tables.accountPrimaryColumn().setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().primary() ? "Yes" : ""));
        tables.accountProviderColumn().setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().providerName()));
        tables.accountNameColumn().setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().accountName()));
        tables.accountReferenceColumn().setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().maskedReference()));
        tables.accountBalanceColumn().setCellValueFactory(cellData ->
                new SimpleStringProperty(AuctionDisplayFormatter.formatCurrency(cellData.getValue().balance())));
        ResponsiveViewSupport.configureResponsiveTable(tables.accountTable());
    }

    static void configureAuctionTable(AuctionTableConfig config) {
        config.watchColumn().setCellValueFactory(cellData -> new SimpleStringProperty(
                config.applicationSession().isAuctionWatched(cellData.getValue().getItemId()) ? "Watching" : ""
        ));
        config.watchColumn().setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("watch-pill", "watch-active");
                if (empty || item == null || item.isBlank()) {
                    setText(null);
                    return;
                }
                setText(item);
                getStyleClass().add("watch-pill");
                getStyleClass().add("watch-active");
            }
        });

        config.nameColumn().setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getItemName()));
        config.statusColumn().setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getStatus()));
        config.statusColumn().setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("status-pill", "status-open", "status-running", "status-finished", "status-paid", "status-cancelled");
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                String normalizedStatus = AuctionCatalogFilters.normalizeStatus(item);
                setText(item.replace('_', ' '));
                getStyleClass().add("status-pill");
                getStyleClass().add(AuctionCatalogFilters.statusStyleClass(normalizedStatus));
            }
        });
        config.currentPriceColumn().setCellValueFactory(cellData ->
                new SimpleObjectProperty<>(cellData.getValue().getCurrentPrice()));
        config.minimumBidColumn().setCellValueFactory(cellData ->
                new SimpleObjectProperty<>(cellData.getValue().getMinimumBid()));
        config.requiredDepositColumn().setCellValueFactory(cellData ->
                new SimpleObjectProperty<>(cellData.getValue().getRequiredDeposit()));
        config.availableBalanceColumn().setCellValueFactory(cellData ->
                new SimpleObjectProperty<>(cellData.getValue().getAvailableBalance()));
        config.timeRemainingColumn().setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getRemainingTime()));
        config.endTimeColumn().setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getEndTimeString()));
        config.eligibleColumn().setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getEligibleText()));
        config.eligibleColumn().setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("eligible-pill", "eligible-entered", "eligible-ready", "eligible-blocked");
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(item);
                getStyleClass().add("eligible-pill");
                getStyleClass().add(config.eligibleStyleClass().apply(item));
            }
        });
        ResponsiveViewSupport.configureResponsiveTable(config.table());
        ResponsiveViewSupport.configureCurrencyColumn(config.currentPriceColumn());
        ResponsiveViewSupport.configureCurrencyColumn(config.minimumBidColumn());
        ResponsiveViewSupport.configureCurrencyColumn(config.requiredDepositColumn());
        ResponsiveViewSupport.configureCurrencyColumn(config.availableBalanceColumn());

        config.table().getSelectionModel().selectedItemProperty().addListener((ignored, previous, current) -> {
            if (config.selectionSuppressed().getAsBoolean()) {
                return;
            }
            if (current == null) {
                config.updateWatchAction().accept(null);
                config.clearBidSection().run();
                return;
            }
            config.selectedAuctionIdSetter().accept(current.getItemId());
            boolean selectionChanged = previous == null || !current.getItemId().equals(previous.getItemId());
            config.updateWatchAction().accept(current);
            config.showSelectedSummary().accept(current, selectionChanged);
            config.refreshSelectedDetail().accept(current);
        });
    }

    static void configureSellerTable(SellerTableConfig config) {
        config.itemNameColumn().setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getItemName()));
        config.statusColumn().setCellValueFactory(cellData ->
                new SimpleObjectProperty<>(cellData.getValue().getApprovalStatus()));
        config.currentPriceColumn().setCellValueFactory(cellData ->
                new SimpleObjectProperty<>(cellData.getValue().getCurrentPrice()));
        config.auctionStartColumn().setCellValueFactory(cellData ->
                new SimpleStringProperty(DashboardFormatters.formatDateTime(cellData.getValue().getStartTime())));
        config.auctionEndColumn().setCellValueFactory(cellData ->
                new SimpleStringProperty(DashboardFormatters.formatDateTime(cellData.getValue().getEndTime())));
        ResponsiveViewSupport.configureResponsiveTable(config.table());
        ResponsiveViewSupport.configureCurrencyColumn(config.currentPriceColumn());

        config.table().getSelectionModel().selectedItemProperty().addListener((ignored, previous, current) -> {
            if (config.selectionSuppressed().getAsBoolean()) {
                return;
            }
            if (current == null) {
                config.bidHistoryList().setItems(FXCollections.observableArrayList());
                config.applySelectionState().accept(null, false);
                return;
            }
            boolean selectionChanged = previous == null || !current.getId().equals(previous.getId());
            if (selectionChanged) {
                config.bidHistoryList().setItems(FXCollections.observableArrayList());
            }
            config.applySelectionState().accept(current, false);
            config.refreshSelection().accept(current);
        });
    }

    static void configureAdminTables(AdminTableConfig config) {
        config.usernameColumn().setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getUsername()));
        config.fullNameColumn().setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getFullName()));
        config.emailColumn().setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getEmail()));
        config.roleColumn().setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getRole()));
        ResponsiveViewSupport.configureResponsiveTable(config.userTable());
        config.roleChoiceBox().setItems(FXCollections.observableArrayList("BIDDER", "SELLER", "ADMIN"));

        config.pendingItemNameColumn().setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getItemName()));
        config.pendingSellerColumn().setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getSellerId()));
        config.pendingStatusColumn().setCellValueFactory(cellData ->
                new SimpleObjectProperty<>(cellData.getValue().getApprovalStatus()));
        ResponsiveViewSupport.configureResponsiveTable(config.pendingItemsTable());
    }

    static void configureSellerItemTypeChoice(ChoiceBox<String> sellerItemTypeChoiceBox) {
        sellerItemTypeChoiceBox.setItems(FXCollections.observableArrayList("electronics", "art", "vehicle"));
        if (!sellerItemTypeChoiceBox.getItems().isEmpty()) {
            sellerItemTypeChoiceBox.setValue(sellerItemTypeChoiceBox.getItems().get(0));
        }
    }

    record WalletTables(
            TableView<WalletTransaction> transactionTable,
            TableColumn<WalletTransaction, String> transactionTimeColumn,
            TableColumn<WalletTransaction, String> transactionTypeColumn,
            TableColumn<WalletTransaction, String> transactionAmountColumn,
            TableColumn<WalletTransaction, String> transactionBalanceColumn,
            TableColumn<WalletTransaction, String> transactionNoteColumn,
            TableView<WalletLinkedAccount> accountTable,
            TableColumn<WalletLinkedAccount, String> accountPrimaryColumn,
            TableColumn<WalletLinkedAccount, String> accountProviderColumn,
            TableColumn<WalletLinkedAccount, String> accountNameColumn,
            TableColumn<WalletLinkedAccount, String> accountReferenceColumn,
            TableColumn<WalletLinkedAccount, String> accountBalanceColumn
    ) {
    }

    record AuctionTableConfig(
            ApplicationSession applicationSession,
            TableView<AuctionEligibilityEntry> table,
            TableColumn<AuctionEligibilityEntry, String> watchColumn,
            TableColumn<AuctionEligibilityEntry, String> nameColumn,
            TableColumn<AuctionEligibilityEntry, String> statusColumn,
            TableColumn<AuctionEligibilityEntry, Double> currentPriceColumn,
            TableColumn<AuctionEligibilityEntry, Double> minimumBidColumn,
            TableColumn<AuctionEligibilityEntry, Double> requiredDepositColumn,
            TableColumn<AuctionEligibilityEntry, Double> availableBalanceColumn,
            TableColumn<AuctionEligibilityEntry, String> timeRemainingColumn,
            TableColumn<AuctionEligibilityEntry, String> endTimeColumn,
            TableColumn<AuctionEligibilityEntry, String> eligibleColumn,
            BooleanSupplier selectionSuppressed,
            Consumer<AuctionEligibilityEntry> updateWatchAction,
            Runnable clearBidSection,
            BiConsumer<AuctionEligibilityEntry, Boolean> showSelectedSummary,
            Consumer<AuctionEligibilityEntry> refreshSelectedDetail,
            Consumer<String> selectedAuctionIdSetter,
            Function<String, String> eligibleStyleClass
    ) {
    }

    record SellerTableConfig(
            TableView<Item> table,
            TableColumn<Item, String> itemNameColumn,
            TableColumn<Item, ApprovalStatus> statusColumn,
            TableColumn<Item, Double> currentPriceColumn,
            TableColumn<Item, String> auctionStartColumn,
            TableColumn<Item, String> auctionEndColumn,
            ListView<String> bidHistoryList,
            BooleanSupplier selectionSuppressed,
            BiConsumer<Item, Boolean> applySelectionState,
            Consumer<Item> refreshSelection
    ) {
    }

    record AdminTableConfig(
            TableView<User> userTable,
            TableColumn<User, String> usernameColumn,
            TableColumn<User, String> fullNameColumn,
            TableColumn<User, String> emailColumn,
            TableColumn<User, String> roleColumn,
            ChoiceBox<String> roleChoiceBox,
            TableView<Item> pendingItemsTable,
            TableColumn<Item, String> pendingItemNameColumn,
            TableColumn<Item, String> pendingSellerColumn,
            TableColumn<Item, ApprovalStatus> pendingStatusColumn
    ) {
    }
}
