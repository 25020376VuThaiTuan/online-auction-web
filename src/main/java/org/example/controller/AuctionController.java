package org.example.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import org.example.auction.AuctionSession;
import org.example.auction.AuctionSessionRegistry;
import org.example.auction.AuctionSeedData;
import org.example.auction.AuctionSummary;
import org.example.auction.BidValidationResult;
import org.example.model.Bid;
import org.example.model.DataManager;
import org.example.model.Item;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class AuctionController {
    @FXML
    private TableView<Item> itemTable;
    @FXML
    private TableColumn<Item, String> nameColumn;
    @FXML
    private TableColumn<Item, Double> priceColumn;
    @FXML
    private TableColumn<Item, String> timeColumn;
    @FXML
    private TextField bidAmountField;

    @FXML
    public void initialize() {
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("currentPrice"));
        timeColumn.setCellValueFactory(new PropertyValueFactory<>("endTimeString"));

        List<Item> savedItems = DataManager.getInstance().loadItems();
        if (savedItems.isEmpty()) {
            savedItems = AuctionSeedData.createDemoItems();
            DataManager.getInstance().saveItems(savedItems);
        }

        AuctionSessionRegistry.getInstance().clear();
        AuctionSessionRegistry.getInstance().preloadSessions(savedItems);

        ObservableList<Item> data = FXCollections.observableArrayList(savedItems);
        itemTable.setItems(data);
    }

    @FXML
    public void handlePlaceBid() {
        Item selectedItem = itemTable.getSelectionModel().getSelectedItem();

        if (selectedItem == null) {
            showAlert(Alert.AlertType.WARNING, "Selection required", "Select an item before placing a bid.");
            return;
        }

        try {
            double bidAmount = Double.parseDouble(bidAmountField.getText());
            AuctionSession session = AuctionSessionRegistry.getInstance().getOrCreateSession(selectedItem);
            Bid bid = new Bid(
                    "BID-" + UUID.randomUUID(),
                    "local-ui-user",
                    selectedItem.getId(),
                    bidAmount,
                    LocalDateTime.now()
            );
            BidValidationResult validation = session.submitBid(bid);

            if (!validation.accepted()) {
                showAlert(Alert.AlertType.WARNING, "Bid rejected", validation.message());
                return;
            }

            itemTable.refresh();
            DataManager.getInstance().saveItems(itemTable.getItems());
            bidAmountField.clear();

            AuctionSummary summary = session.getSummary();
            showAlert(
                    Alert.AlertType.INFORMATION,
                    "Bid accepted",
                    validation.message()
                            + "\nCurrent price: " + summary.currentPrice()
                            + "\nNext minimum bid: " + summary.minimumNextBid()
                            + "\nAuction ends: " + selectedItem.getEndTimeString()
            );
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Invalid amount", "Enter a valid bid amount.");
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
