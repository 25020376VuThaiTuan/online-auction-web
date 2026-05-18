package org.example.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.example.auction.AuctionDepositResult;
import org.example.auction.AuctionRules;
import org.example.auction.AuctionSettlement;
import org.example.auction.AuctionSettlementStatus;
import org.example.auction.AuctionStatus;
import org.example.auction.AuctionSummary;
import org.example.auction.BidValidationResult;
import org.example.client.AuctionApiClient;
import org.example.client.AuctionApiClient.AuctionDetail;
import org.example.client.AuctionApiClient.EntryDepositResponse;
import org.example.client.AuctionApiClient.SettlementDetail;
import org.example.model.Bid;
import org.example.model.Item;
import org.example.service.MarketplaceDashboardService;
import org.example.service.AuctionWorkflowService;
import org.example.state.ApplicationSession;
import org.example.util.AuctionDisplayFormatter;
import org.example.util.ResponsiveViewSupport;
import org.example.util.SceneNavigator;

import java.time.LocalDateTime;

public class AuctionController {
    private static final java.time.Duration WALLET_PIN_TRUST_DURATION = java.time.Duration.ofMinutes(120);

    private final AuctionApiClient apiClient = AuctionApiClient.getInstance();
    private final AuctionWorkflowService workflowService = AuctionWorkflowService.getInstance();
    private final MarketplaceDashboardService dashboardService = MarketplaceDashboardService.getInstance();
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
    private Label bidEntryTimeRemainingLabel;

    @FXML
    private Label depositLabel;

    @FXML
    private Label settlementLabel;

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
    private Button confirmEntryButton;
    
    @FXML
    private Button admitResultButton;

    @FXML
    private Button confirmReceivedButton;

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
            String selectedAmount = selectedBidAmountText();
            if (selectedAmount == null || selectedAmount.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Invalid amount", "Please enter or select a bid amount.");
                return;
            }
            double bidAmount = parseAmount(selectedAmount);
            
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to place a bid of $" + bidAmount + "?", ButtonType.YES, ButtonType.NO);
            confirmAlert.setTitle("Transaction Verification");
            confirmAlert.setHeaderText(null);
            confirmAlert.showAndWait();
            
            if (confirmAlert.getResult() != ButtonType.YES) {
                return;
            }

            String walletPin = requestWalletPin("Place Bid");
            if (walletPin == null) {
                return;
            }

            BidValidationResult result = useApi()
                    ? apiClient.placeBid(apiToken(), selectedAuctionId, bidAmount, walletPin)
                    : dashboardService.placeBidWithDeposit(
                            selectedAuctionId,
                            applicationSession.getCurrentUser().orElseThrow(),
                            bidAmount,
                            walletPin
                    );

            if (!result.accepted()) {
                refreshView();
                showAlert(Alert.AlertType.WARNING, "Bid rejected", result.message());
                return;
            }

            bidAmountCombo.setValue(null);
            bidAmountCombo.getEditor().clear();
            refreshView();
            showAlert(Alert.AlertType.INFORMATION, "Bid accepted", result.message());
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Invalid amount", "Enter a valid numeric bid amount.");
        } catch (AuctionApiClient.ApiClientException | IllegalArgumentException | IllegalStateException e) {
            showAlert(Alert.AlertType.WARNING, "Bid failed", e.getMessage());
        }
    }

    @FXML
    public void handleConfirmEntryDeposit() {
        if (applicationSession.getCurrentUser().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Authentication required", "Please sign in again.");
            handleLogout();
            return;
        }

        try {
            String walletPin = requestWalletPin("Confirm Entry Deposit");
            if (walletPin == null) {
                return;
            }
            AuctionDepositResult result;
            if (useApi()) {
                EntryDepositResponse response = apiClient.confirmAuctionEntry(apiToken(), selectedAuctionId, walletPin);
                applicationSession.replaceCurrentUser(response.user());
                result = response.result();
            } else {
                result = dashboardService.confirmAuctionEntry(
                        selectedAuctionId,
                        applicationSession.getCurrentUser().orElseThrow(),
                        walletPin
                );
            }
            refreshView();
            showAlert(result.accepted() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING,
                    result.accepted() ? "Deposit locked" : "Deposit not locked",
                    result.message());
        } catch (AuctionApiClient.ApiClientException | IllegalArgumentException | IllegalStateException e) {
            showAlert(Alert.AlertType.WARNING, "Deposit failed", e.getMessage());
        }
    }
    
    @FXML
    public void handleAdmitResult() {
        String walletPin = requestWalletPin("Admit Result");
        if (walletPin == null) {
            return;
        }
        runSettlementAction("Result admitted", () -> {
            if (useApi()) {
                apiClient.admitWinnerResult(apiToken(), selectedAuctionId, walletPin);
                refreshApiCurrentUser();
            } else {
                dashboardService.admitWinnerResult(selectedAuctionId, applicationSession.getCurrentUser().orElseThrow(), walletPin);
            }
        });
    }

    @FXML
    public void handleConfirmReceived() {
        String walletPin = requestWalletPin("Confirm Received");
        if (walletPin == null) {
            return;
        }
        runSettlementAction("Payment confirmed", () -> {
            if (useApi()) {
                apiClient.confirmGoodsReceived(apiToken(), selectedAuctionId, walletPin);
                refreshApiCurrentUser();
            } else {
                dashboardService.confirmGoodsReceived(selectedAuctionId, applicationSession.getCurrentUser().orElseThrow(), walletPin);
            }
        });
    }

    @FXML
    private void handleBack() {
        stopRefreshLoop();
        SceneNavigator.switchScene(placeBidButton, "/view/AuctionList.fxml", "Auction Catalog");
    }

    @FXML
    private void handleLogout() {
        if (useApi()) {
            try {
                apiClient.logout(apiToken());
            } catch (AuctionApiClient.ApiClientException ignored) {
            }
        }
        applicationSession.logout();
        stopRefreshLoop();
        SceneNavigator.switchScene(placeBidButton, "/view/Login.fxml", "Online Auction System");
    }

    private void refreshView() {
        if (useApi()) {
            refreshViewFromApi();
            return;
        }

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
        bidEntryTimeRemainingLabel.setText("Time remaining: " + AuctionDisplayFormatter.formatRemainingTime(summary.secondsRemaining()));
        bidTable.setItems(FXCollections.observableArrayList(workflowService.getBidHistory(selectedAuctionId)));
        bidTable.refresh();

        // This guard enforces the rubric rule: only active auctions accept bids.
        boolean depositConfirmed = applicationSession.getCurrentUser()
                .map(user -> dashboardService.hasConfirmedEntryDeposit(selectedAuctionId, user))
                .orElse(false);
        double requiredDeposit = AuctionRules.requiredDeposit(summary.currentPrice());
        depositLabel.setText("Entry deposit: " + AuctionDisplayFormatter.formatCurrency(requiredDeposit)
                + (depositConfirmed ? " locked" : " not locked"));
        boolean canBid = summary.status() == AuctionStatus.RUNNING
                && applicationSession.getCurrentUser().isPresent()
                && depositConfirmed;
        bidAmountCombo.setDisable(!canBid);
        placeBidButton.setDisable(!canBid);
        confirmEntryButton.setDisable(!applicationSession.getCurrentUser().isPresent()
                || summary.status().isFinished()
                || depositConfirmed);
        refreshLocalSettlementActions();
        
        refreshBidAmountSuggestions(summary.minimumNextBid());
    }

    private void refreshViewFromApi() {
        AuctionDetail detail = apiClient.getAuction(apiToken(), selectedAuctionId);
        itemNameLabel.setText(detail.itemName());
        descriptionLabel.setText(detail.description());
        statusLabel.setText(detail.status().replace('_', ' '));
        currentPriceLabel.setText(AuctionDisplayFormatter.formatCurrency(detail.currentPrice()));
        minimumBidLabel.setText(AuctionDisplayFormatter.formatCurrency(detail.minimumNextBid()));
        endTimeLabel.setText(detail.displayEndTime());
        timeRemainingLabel.setText(AuctionDisplayFormatter.formatRemainingTime(detail.secondsRemaining()));
        bidEntryTimeRemainingLabel.setText("Time remaining: " + AuctionDisplayFormatter.formatRemainingTime(detail.secondsRemaining()));
        bidTable.setItems(FXCollections.observableArrayList(apiClient.getBidHistory(apiToken(), selectedAuctionId)));
        bidTable.refresh();

        depositLabel.setText("Entry deposit: " + AuctionDisplayFormatter.formatCurrency(detail.requiredDeposit())
                + (detail.depositConfirmed() ? " locked" : " not locked"));
        boolean canBid = "RUNNING".equalsIgnoreCase(detail.status())
                && applicationSession.getCurrentUser().isPresent()
                && detail.depositConfirmed();
        bidAmountCombo.setDisable(!canBid);
        placeBidButton.setDisable(!canBid);
        confirmEntryButton.setDisable(!applicationSession.getCurrentUser().isPresent()
                || isFinishedStatus(detail.status())
                || detail.depositConfirmed());
        refreshApiSettlementActions();
        refreshBidAmountSuggestions(detail.minimumNextBid());
    }

    private String selectedBidAmountText() {
        String editorText = bidAmountCombo.getEditor() == null ? "" : bidAmountCombo.getEditor().getText();
        if (editorText != null && !editorText.trim().isEmpty()) {
            return editorText.trim();
        }
        String selectedValue = bidAmountCombo.getValue();
        return selectedValue == null ? "" : selectedValue.trim();
    }

    private String requestWalletPin(String title) {
        String userId = applicationSession.getCurrentUser()
                .map(user -> user.getId())
                .orElse("");
        var trustedAuthorization = applicationSession.getTrustedWalletAuthorization(userId);
        if (trustedAuthorization.isPresent()) {
            return trustedAuthorization.get();
        }

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        PasswordField pinField = new PasswordField();
        pinField.setPromptText("Wallet PIN");
        CheckBox rememberPin = new CheckBox("Do not ask PIN for 120 minutes");
        dialog.getDialogPane().setContent(new VBox(10.0, pinField, rememberPin));
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(button -> button == ButtonType.OK ? pinField.getText().trim() : null);
        Platform.runLater(pinField::requestFocus);
        String pin = dialog.showAndWait().orElse(null);
        if (pin == null) {
            return null;
        }
        if (pin.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Wallet PIN required", "Enter your wallet PIN.");
            return null;
        }
        if (rememberPin.isSelected()) {
            try {
                var authorization = useApi()
                        ? apiClient.authorizeWallet(apiToken(), pin)
                        : dashboardService.authorizeWallet(
                                applicationSession.getCurrentUser().orElseThrow(),
                                pin,
                                WALLET_PIN_TRUST_DURATION
                        );
                applicationSession.trustWalletAuthorization(userId, authorization.token(), authorization.expiresAt());
                return authorization.token();
            } catch (AuctionApiClient.ApiClientException | IllegalArgumentException | IllegalStateException e) {
                showAlert(Alert.AlertType.WARNING, "Wallet authorization failed", e.getMessage());
            }
        }
        return pin;
    }

    private double parseAmount(String text) {
        String normalized = text == null ? "" : text.trim().replace("$", "").replace(",", "");
        if (normalized.isBlank()) {
            throw new NumberFormatException("Amount is blank.");
        }
        return Double.parseDouble(normalized);
    }

    private void refreshBidAmountSuggestions(double minimumNextBid) {
        bidAmountCombo.setPromptText("Min " + AuctionDisplayFormatter.formatCurrency(minimumNextBid));
        if (bidAmountCombo.getItems().isEmpty()
                || !bidAmountCombo.getItems().get(0).equals(String.valueOf(minimumNextBid))) {
            bidAmountCombo.setItems(FXCollections.observableArrayList(
                    String.valueOf(minimumNextBid),
                    String.valueOf(minimumNextBid + 10),
                    String.valueOf(minimumNextBid + 50),
                    String.valueOf(minimumNextBid + 100)
            ));
        }
    }

    private void startRefreshLoop() {
        // The detail screen refreshes itself so status, timers, and bid history stay near real-time.
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> refreshView()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    private void refreshLocalSettlementActions() {
        AuctionSettlement settlement = dashboardService.getSettlement(selectedAuctionId).orElse(null);
        String currentUserId = applicationSession.getCurrentUser().map(user -> user.getId()).orElse("");
        if (settlement == null) {
            settlementLabel.setText("Settlement: N/A");
            setBuyerSettlementButtonsDisabled(true, true);
            return;
        }

        settlementLabel.setText("Settlement: " + settlement.getDisplaySummary());
        boolean isWinner = settlement.getWinnerBidderId().equals(currentUserId);
        admitResultButton.setDisable(!isWinner
                || settlement.getStatus() != AuctionSettlementStatus.AWAITING_WINNER_ADMISSION);
        boolean awaitingBuyer = isWinner
                && settlement.getStatus() == AuctionSettlementStatus.AWAITING_BUYER_CONFIRMATION;
        confirmReceivedButton.setDisable(!awaitingBuyer);
    }

    private void refreshApiSettlementActions() {
        SettlementDetail settlement = apiClient.getSettlement(apiToken(), selectedAuctionId);
        String currentUserId = applicationSession.getCurrentUser().map(user -> user.getId()).orElse("");
        if (settlement == null) {
            settlementLabel.setText("Settlement: N/A");
            setBuyerSettlementButtonsDisabled(true, true);
            return;
        }

        settlementLabel.setText("Settlement: " + settlement.displaySummary());
        boolean isWinner = settlement.winnerBidderId().equals(currentUserId);
        admitResultButton.setDisable(!isWinner || !"AWAITING_WINNER_ADMISSION".equals(settlement.status()));
        boolean awaitingBuyer = isWinner && "AWAITING_BUYER_CONFIRMATION".equals(settlement.status());
        confirmReceivedButton.setDisable(!awaitingBuyer);
    }

    private void setBuyerSettlementButtonsDisabled(boolean admitDisabled, boolean confirmDisabled) {
        admitResultButton.setDisable(admitDisabled);
        confirmReceivedButton.setDisable(confirmDisabled);
    }

    private void runSettlementAction(String title, Runnable action) {
        if (applicationSession.getCurrentUser().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Authentication required", "Please sign in again.");
            handleLogout();
            return;
        }
        try {
            action.run();
            refreshView();
            showAlert(Alert.AlertType.INFORMATION, title, "Settlement was updated.");
        } catch (AuctionApiClient.ApiClientException | IllegalArgumentException | IllegalStateException e) {
            refreshView();
            showAlert(Alert.AlertType.WARNING, title + " failed", e.getMessage());
        }
    }

    private void refreshApiCurrentUser() {
        applicationSession.replaceCurrentUser(apiClient.getCurrentUser(apiToken()));
    }

    private boolean isFinishedStatus(String status) {
        return "FINISHED".equalsIgnoreCase(status)
                || "PAID".equalsIgnoreCase(status)
                || "CANCELLED".equalsIgnoreCase(status);
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

    private boolean useApi() {
        return apiClient.isEnabled() && applicationSession.getApiToken().isPresent();
    }

    private String apiToken() {
        return applicationSession.getApiToken()
                .orElseThrow(() -> new IllegalStateException("No API token in session."));
    }
}
