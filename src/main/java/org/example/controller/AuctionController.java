package org.example.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Duration;
import org.example.auction.AuctionStatus;
import org.example.auction.AuctionSummary;
import org.example.auction.BidValidationResult;
import org.example.model.Bid;
import org.example.model.Item;
import org.example.service.AuctionWorkflowService;
import org.example.state.ApplicationSession;
import org.example.util.AuctionDisplayFormatter;
import org.example.util.ResponsiveViewSupport;
import org.example.util.SceneNavigator;

import java.time.LocalDateTime;

public class AuctionController {
    private final AuctionWorkflowService workflowService = AuctionWorkflowService.getInstance();
    private final ApplicationSession applicationSession = ApplicationSession.getInstance();

    private Timeline refreshTimeline;
    private String selectedAuctionId;

    @FXML
    private Label userLabel;

    @FXML
    private Label itemNameLabel;

    @FXML
    private Label descriptionLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label currentPriceLabel;

    @FXML
    private Label minimumBidLabel;

    @FXML
    private Label endTimeLabel;

    @FXML
    private Label timeRemainingLabel;

    @FXML
    private TableView<Bid> bidTable;

    @FXML
    private TableColumn<Bid, String> bidderColumn;

    @FXML
    private TableColumn<Bid, Double> amountColumn;

    @FXML
    private TableColumn<Bid, LocalDateTime> timeColumn;

    @FXML
    private TextField bidAmountField;

    @FXML
    private Button placeBidButton;

    @FXML
    public void initialize() {
        selectedAuctionId = applicationSession.getSelectedAuctionId().orElse(null);
        if (selectedAuctionId == null) {
            Platform.runLater(() -> SceneNavigator.switchScene(bidAmountField, "/view/AuctionList.fxml", "Auction Catalog"));
            return;
        }

        bidderColumn.setCellValueFactory(new PropertyValueFactory<>("bidderId"));
        amountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        timeColumn.setCellValueFactory(new PropertyValueFactory<>("bidTime"));
        ResponsiveViewSupport.configureResponsiveTable(bidTable);
        ResponsiveViewSupport.configureCurrencyColumn(amountColumn);
        timeColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? "" : AuctionDisplayFormatter.formatDateTime(item));
            }
        });

        userLabel.setText("Signed in as: " + applicationSession.getCurrentUserLabel());
        refreshView();
        startRefreshLoop();
    }

    @FXML
    public void handlePlaceBid() {
        if (applicationSession.getCurrentUser().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Authentication required", "Please sign in again.");
            handleLogout();
            return;
        }

        try {
            double bidAmount = Double.parseDouble(bidAmountField.getText());
            BidValidationResult result = workflowService.placeBid(
                    selectedAuctionId,
                    applicationSession.getCurrentUser().orElseThrow(),
                    bidAmount
            );

            if (!result.accepted()) {
                refreshView();
                showAlert(Alert.AlertType.WARNING, "Bid rejected", result.message());
                return;
            }

            bidAmountField.clear();
            refreshView();
            showAlert(Alert.AlertType.INFORMATION, "Bid accepted", result.message());
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Invalid amount", "Enter a valid numeric bid amount.");
        }
    }

    @FXML
    private void handleBack() {
        stopRefreshLoop();
        SceneNavigator.switchScene(bidAmountField, "/view/AuctionList.fxml", "Auction Catalog");
    }

    @FXML
    private void handleLogout() {
        applicationSession.logout();
        stopRefreshLoop();
        SceneNavigator.switchScene(bidAmountField, "/view/Login.fxml", "Online Auction System");
    }

    private void refreshView() {
        workflowService.refreshFromStoreIfChanged();
        Item item = workflowService.findItemById(selectedAuctionId).orElse(null);
        if (item == null) {
            showAlert(Alert.AlertType.WARNING, "Auction missing", "The selected auction no longer exists.");
            handleBack();
            return;
        }

        AuctionSummary summary = workflowService.getSummary(selectedAuctionId);
        itemNameLabel.setText(item.getItemName());
        descriptionLabel.setText(item.getDescription());
        statusLabel.setText(summary.status().name().replace('_', ' '));
        currentPriceLabel.setText(AuctionDisplayFormatter.formatCurrency(summary.currentPrice()));
        minimumBidLabel.setText(AuctionDisplayFormatter.formatCurrency(summary.minimumNextBid()));
        endTimeLabel.setText(item.getEndTimeString());
        timeRemainingLabel.setText(AuctionDisplayFormatter.formatRemainingTime(summary.secondsRemaining()));
        bidTable.setItems(FXCollections.observableArrayList(workflowService.getBidHistory(selectedAuctionId)));
        bidTable.refresh();

        // This guard enforces the rubric rule: only active auctions accept bids.
        boolean canBid = summary.status() == AuctionStatus.RUNNING && applicationSession.getCurrentUser().isPresent();
        bidAmountField.setDisable(!canBid);
        placeBidButton.setDisable(!canBid);
    }

    private void startRefreshLoop() {
        // The detail screen refreshes itself so status, timers, and bid history stay near real-time.
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> refreshView()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    private void stopRefreshLoop() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
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
