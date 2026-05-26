package org.example.controller;

import javafx.collections.FXCollections;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import org.example.auction.AuctionSettlement;
import org.example.model.Item;
import org.example.model.User;

import java.util.List;

final class DashboardAdminPresenter {
    private final TableView<User> userTable;
    private final TableView<Item> pendingItemsTable;
    private final ListView<String> adminSettlementList;
    private final ListView<String> adminWalletAuditList;
    private final List<AuctionSettlement> adminSettlementItems;

    private String lastRefreshFailureMessage;

    DashboardAdminPresenter(
            TableView<User> userTable,
            TableView<Item> pendingItemsTable,
            ListView<String> adminSettlementList,
            ListView<String> adminWalletAuditList,
            List<AuctionSettlement> adminSettlementItems
    ) {
        this.userTable = userTable;
        this.pendingItemsTable = pendingItemsTable;
        this.adminSettlementList = adminSettlementList;
        this.adminWalletAuditList = adminWalletAuditList;
        this.adminSettlementItems = adminSettlementItems;
    }

    void apply(
            boolean adminSection,
            boolean userIsAdmin,
            String failureMessage,
            List<User> users,
            List<Item> pendingItems,
            List<String> settlementLines,
            List<AuctionSettlement> settlementItems
    ) {
        if (!adminSection || !userIsAdmin) {
            clear();
            return;
        }
        if (failureMessage != null) {
            handleRefreshFailure(failureMessage);
            return;
        }

        userTable.setItems(FXCollections.observableArrayList(users));
        pendingItemsTable.setItems(FXCollections.observableArrayList(pendingItems));
        adminSettlementItems.clear();
        adminSettlementItems.addAll(settlementItems);
        adminSettlementList.setItems(FXCollections.observableArrayList(settlementLines));
        if (lastRefreshFailureMessage != null) {
            adminWalletAuditList.setItems(FXCollections.observableArrayList());
            lastRefreshFailureMessage = null;
        }
    }

    void clear() {
        userTable.setItems(FXCollections.observableArrayList());
        pendingItemsTable.setItems(FXCollections.observableArrayList());
        adminSettlementItems.clear();
        adminSettlementList.setItems(FXCollections.observableArrayList());
        adminWalletAuditList.setItems(FXCollections.observableArrayList());
        lastRefreshFailureMessage = null;
    }

    void handleRefreshFailure(String message) {
        String resolvedMessage = message == null || message.isBlank()
                ? "Admin data is temporarily unavailable."
                : message;
        lastRefreshFailureMessage = resolvedMessage;
        userTable.setItems(FXCollections.observableArrayList());
        pendingItemsTable.setItems(FXCollections.observableArrayList());
        adminSettlementItems.clear();
        adminSettlementList.setItems(FXCollections.observableArrayList("Admin data unavailable."));
        adminWalletAuditList.setItems(FXCollections.observableArrayList(resolvedMessage));
    }
}
