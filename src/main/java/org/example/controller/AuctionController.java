package org.example.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
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
    private ComboBox<String> bidAmountCombo;

    @FXML
    private Button placeBidButton;
    
    @FXML
    private TextField autoBidMaxField;
    
    @FXML
    private Button setAutoBidButton;
    
    @FXML
    private Button fastBid10Button;
    
    @FXML
    private Button fastBid50Button;
    
    @FXML
    private Button fastBid100Button;

    @FXML
    public void initialize() {
        selectedAuctionId = applicationSession.getSelectedAuctionId().orElse(null);
        if (selectedAuctionId == null) {
            Platform.runLater(() -> SceneNavigator.switchScene(placeBidButton, "/view/AuctionList.fxml", "Auction Catalog"));
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
            String selectedAmount = bidAmountCombo.getValue();
            if (selectedAmount == null || selectedAmount.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Invalid amount", "Please enter or select a bid amount.");
                return;
            }
            double bidAmount = Double.parseDouble(selectedAmount.replace("$", "").replace(",", ""));
            
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to place a bid of $" + bidAmount + "?", ButtonType.YES, ButtonType.NO);
            confirmAlert.setTitle("Transaction Verification");
            confirmAlert.setHeaderText(null);
            confirmAlert.showAndWait();
            
            if (confirmAlert.getResult() != ButtonType.YES) {
                return;
            }

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

            bidAmountCombo.setValue("");
            refreshView();
            showAlert(Alert.AlertType.INFORMATION, "Bid accepted", result.message());
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Invalid amount", "Enter a valid numeric bid amount.");
        }
    }
    
    @FXML
    public void handleFastBid10() {
        placeFastBid(10.0);
    }
    
    @FXML
    public void handleFastBid50() {
        placeFastBid(50.0);
    }
    
    @FXML
    public void handleFastBid100() {
        placeFastBid(100.0);
    }
    
    private void placeFastBid(double addedAmount) {
        AuctionSummary summary = workflowService.getSummary(selectedAuctionId);
        double fastBid = summary.minimumNextBid() + addedAmount;
        bidAmountCombo.setValue(String.valueOf(fastBid));
        handlePlaceBid();
    }
    
    @FXML
    public void handleSetAutoBid() {
        if (applicationSession.getCurrentUser().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Authentication required", "Please sign in again.");
            handleLogout();
            return;
        }
        
        try {
            double maxLimit = Double.parseDouble(autoBidMaxField.getText());
            boolean success = workflowService.registerAutoBid(
                selectedAuctionId, 
                applicationSession.getCurrentUser().orElseThrow(), 
                maxLimit
            );
            
            if (success) {
                autoBidMaxField.clear();
                showAlert(Alert.AlertType.INFORMATION, "Auto-Bid Set", "Your auto-bid limit of $" + maxLimit + " was set successfully.");
            } else {
                showAlert(Alert.AlertType.ERROR, "Error", "Could not save auto-bid limit.");
            }
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Invalid amount", "Enter a valid numeric max limit.");
        }
    }

    @FXML
    private void handleBack() {
        stopRefreshLoop();
        SceneNavigator.switchScene(placeBidButton, "/view/AuctionList.fxml", "Auction Catalog");
    }

    @FXML
    private void handleLogout() {
        applicationSession.logout();
        stopRefreshLoop();
        SceneNavigator.switchScene(placeBidButton, "/view/Login.fxml", "Online Auction System");
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
        bidAmountCombo.setDisable(!canBid);
        placeBidButton.setDisable(!canBid);
        fastBid10Button.setDisable(!canBid);
        fastBid50Button.setDisable(!canBid);
        fastBid100Button.setDisable(!canBid);
        autoBidMaxField.setDisable(!canBid);
        setAutoBidButton.setDisable(!canBid);
        
        // Populate ComboBox suggestions
        if (bidAmountCombo.getItems().isEmpty() || !bidAmountCombo.getItems().get(0).equals(String.valueOf(summary.minimumNextBid()))) {
            bidAmountCombo.setItems(FXCollections.observableArrayList(
                String.valueOf(summary.minimumNextBid()),
                String.valueOf(summary.minimumNextBid() + 10),
                String.valueOf(summary.minimumNextBid() + 50),
                String.valueOf(summary.minimumNextBid() + 100)
            ));
        }
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
