package org.example.controller;

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
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
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
import org.example.util.BackgroundExecutorFactory;
import org.example.util.ResponsiveViewSupport;
import org.example.util.SceneNavigator;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class AuctionController {
    private static final java.time.Duration WALLET_PIN_TRUST_DURATION = java.time.Duration.ofMinutes(120);
    private static final int REFRESH_INTERVAL_MILLIS = 2_000;
    private static final DateTimeFormatter BID_NOTIFICATION_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");

    private final AuctionApiClient apiClient = AuctionApiClient.getInstance();
    private final AuctionWorkflowService workflowService = AuctionWorkflowService.getInstance();
    private final MarketplaceDashboardService dashboardService = MarketplaceDashboardService.getInstance();
    private final ApplicationSession applicationSession = ApplicationSession.getInstance();
    private final ExecutorService refreshExecutor = BackgroundExecutorFactory.newSingleThreadExecutor("auction-detail-refresh");
    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);

    private javafx.animation.Timeline refreshTimeline;
    private String selectedAuctionId;
    private volatile boolean refreshActive;
    private String lastRefreshFailureMessage;

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
    private Label currentWinnerLabel;

    @FXML
    private ListView<String> bidNotificationList;

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
        refreshActive = true;
        refreshViewAsync(true);
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
            if (selectedAmount.isEmpty()) {
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
                refreshViewAsync(false);
                showAlert(Alert.AlertType.WARNING, "Bid rejected", result.message());
                return;
            }

            String bidderName = applicationSession.getCurrentUser()
                    .map(user -> user.getFullName())
                    .orElse(applicationSession.getCurrentUserLabel());
            addBidActivityNotification(bidderName, bidAmount, LocalDateTime.now(), "accepted");
            readyBidAmountInput(AuctionRules.minimumNextBid(bidAmount), true);
            refreshViewAsync(false);
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
            refreshViewAsync(false);
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

    private void refreshViewAsync(boolean initialLoad) {
        if (!refreshActive || !refreshInFlight.compareAndSet(false, true)) {
            return;
        }

        CompletableFuture
                .supplyAsync(this::loadAuctionViewSnapshot, refreshExecutor)
                .whenComplete((snapshot, throwable) -> Platform.runLater(() -> {
                    try {
                        if (!refreshActive) {
                            return;
                        }
                        if (throwable != null) {
                            handleRefreshFailure(refreshFailureMessage(throwable), initialLoad);
                            return;
                        }
                        if (snapshot.missingAuction()) {
                            showAlert(Alert.AlertType.WARNING, "Auction missing", "The selected auction no longer exists.");
                            handleBack();
                            return;
                        }
                        applyAuctionViewSnapshot(snapshot);
                        lastRefreshFailureMessage = null;
                    } finally {
                        refreshInFlight.set(false);
                    }
                }));
    }

    private AuctionViewSnapshot loadAuctionViewSnapshot() {
        if (useApi()) {
            return loadAuctionViewSnapshotFromApi();
        }

        workflowService.refreshFromStoreIfChanged();
        Item item = workflowService.findItemById(selectedAuctionId).orElse(null);
        if (item == null) {
            return AuctionViewSnapshot.missing();
        }

        AuctionSummary summary = workflowService.getSummary(selectedAuctionId);
        boolean depositConfirmed = applicationSession.getCurrentUser()
                .map(user -> dashboardService.hasConfirmedEntryDeposit(selectedAuctionId, user))
                .orElse(false);
        double requiredDeposit = AuctionRules.requiredDeposit(summary.currentPrice());
        boolean canBid = summary.status() == AuctionStatus.RUNNING
                && applicationSession.getCurrentUser().isPresent()
                && depositConfirmed;
        AuctionSettlement settlement = dashboardService.getSettlement(selectedAuctionId).orElse(null);
        String currentUserId = applicationSession.getCurrentUser().map(user -> user.getId()).orElse("");
        SettlementState settlementState = localSettlementState(settlement, currentUserId);

        return new AuctionViewSnapshot(
                false,
                item.getItemName(),
                item.getDescription(),
                summary.status().name(),
                summary.currentPrice(),
                summary.minimumNextBid(),
                item.getEndTimeString(),
                summary.secondsRemaining(),
                workflowService.getBidHistory(selectedAuctionId),
                requiredDeposit,
                depositConfirmed,
                canBid,
                !applicationSession.getCurrentUser().isPresent() || summary.status().isFinished() || depositConfirmed,
                settlementState.summary(),
                settlementState.admitDisabled(),
                settlementState.confirmDisabled()
        );
    }

    private AuctionViewSnapshot loadAuctionViewSnapshotFromApi() {
        AuctionDetail detail = apiClient.getAuction(apiToken(), selectedAuctionId);
        boolean canBid = "RUNNING".equalsIgnoreCase(detail.status())
                && applicationSession.getCurrentUser().isPresent()
                && detail.depositConfirmed();
        SettlementDetail settlement = apiClient.getSettlement(apiToken(), selectedAuctionId);
        String currentUserId = applicationSession.getCurrentUser().map(user -> user.getId()).orElse("");
        SettlementState settlementState = apiSettlementState(settlement, currentUserId);
        return new AuctionViewSnapshot(
                false,
                detail.itemName(),
                detail.description(),
                detail.status(),
                detail.currentPrice(),
                detail.minimumNextBid(),
                detail.displayEndTime(),
                detail.secondsRemaining(),
                apiClient.getBidHistory(apiToken(), selectedAuctionId),
                detail.requiredDeposit(),
                detail.depositConfirmed(),
                canBid,
                !applicationSession.getCurrentUser().isPresent() || isFinishedStatus(detail.status()) || detail.depositConfirmed(),
                settlementState.summary(),
                settlementState.admitDisabled(),
                settlementState.confirmDisabled()
        );
    }

    private void applyAuctionViewSnapshot(AuctionViewSnapshot snapshot) {
        itemNameLabel.setText(snapshot.itemName());
        descriptionLabel.setText(snapshot.description());
        statusLabel.setText(snapshot.status().replace('_', ' '));
        currentPriceLabel.setText(AuctionDisplayFormatter.formatCurrency(snapshot.currentPrice()));
        minimumBidLabel.setText(AuctionDisplayFormatter.formatCurrency(snapshot.minimumNextBid()));
        endTimeLabel.setText(snapshot.displayEndTime());
        timeRemainingLabel.setText(AuctionDisplayFormatter.formatRemainingTime(snapshot.secondsRemaining()));
        bidEntryTimeRemainingLabel.setText("Time remaining: " + AuctionDisplayFormatter.formatRemainingTime(snapshot.secondsRemaining()));
        bidTable.setItems(FXCollections.observableArrayList(snapshot.bids()));
        bidTable.refresh();
        applyBidStatusViews(snapshot.bids());

        depositLabel.setText("Entry deposit: " + AuctionDisplayFormatter.formatCurrency(snapshot.requiredDeposit())
                + (snapshot.depositConfirmed() ? " locked" : " not locked"));
        bidAmountCombo.setDisable(!snapshot.canBid());
        placeBidButton.setDisable(!snapshot.canBid());
        confirmEntryButton.setDisable(snapshot.confirmEntryDisabled());
        settlementLabel.setText(snapshot.settlementSummary());
        setBuyerSettlementButtonsDisabled(snapshot.admitResultDisabled(), snapshot.confirmReceivedDisabled());
        refreshBidAmountSuggestions(snapshot.minimumNextBid(), snapshot.canBid());
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

    private void refreshBidAmountSuggestions(double minimumNextBid, boolean canBid) {
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
        if (canBid) {
            readyBidAmountInput(minimumNextBid, false);
        }
    }

    private void readyBidAmountInput(double minimumBid, boolean force) {
        if (bidAmountCombo == null || bidAmountCombo.isDisabled()) {
            return;
        }

        String currentAmount = selectedBidAmountText();
        if (!force && !currentAmount.isBlank()) {
            try {
                if (parseAmount(currentAmount) >= minimumBid) {
                    return;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        String formattedAmount = formatAmountInput(minimumBid);
        bidAmountCombo.setValue(formattedAmount);
        if (bidAmountCombo.getEditor() != null) {
            bidAmountCombo.getEditor().setText(formattedAmount);
        }
    }

    private String formatAmountInput(double amount) {
        return String.format(Locale.US, "%.2f", amount);
    }

    private void applyBidStatusViews(List<Bid> bids) {
        List<Bid> safeBids = bids == null ? List.of() : bids;
        if (safeBids.isEmpty()) {
            currentWinnerLabel.setText("Current winner: No bids yet");
            bidNotificationList.setItems(FXCollections.observableArrayList());
            return;
        }

        Bid winningBid = safeBids.get(safeBids.size() - 1);
        currentWinnerLabel.setText("Current winner: "
                + displayBidderName(winningBid.getBidderId())
                + " - "
                + AuctionDisplayFormatter.formatCurrency(winningBid.getAmount())
                + " at "
                + formatBidNotificationTime(winningBid.getBidTime()));

        List<String> lines = new ArrayList<>();
        for (int index = safeBids.size() - 1; index >= 0 && lines.size() < 10; index--) {
            lines.add(formatBidNotificationLine(safeBids.get(index)));
        }
        bidNotificationList.setItems(FXCollections.observableArrayList(lines));
    }

    private String formatBidNotificationLine(Bid bid) {
        return formatBidNotificationTime(bid.getBidTime())
                + " - "
                + displayBidderName(bid.getBidderId())
                + " - "
                + AuctionDisplayFormatter.formatCurrency(bid.getAmount());
    }

    private String formatBidNotificationTime(LocalDateTime value) {
        return value == null ? "N/A" : BID_NOTIFICATION_TIME_FORMATTER.format(value);
    }

    private String displayBidderName(String bidderId) {
        String safeBidderId = bidderId == null ? "" : bidderId.trim();
        if (safeBidderId.isBlank()) {
            return "Unknown bidder";
        }
        var currentUser = applicationSession.getCurrentUser();
        if (currentUser.isPresent() && safeBidderId.equals(currentUser.get().getId())) {
            return currentUser.get().getFullName();
        }
        try {
            return dashboardService.findUserById(safeBidderId)
                    .map(user -> user.getFullName())
                    .filter(name -> !name.isBlank())
                    .orElse(safeBidderId);
        } catch (RuntimeException ignored) {
            return safeBidderId;
        }
    }

    private void addBidActivityNotification(String bidderName, double amount, LocalDateTime bidTime, String status) {
        List<String> lines = new ArrayList<>(bidNotificationList.getItems());
        lines.add(0, formatBidNotificationTime(bidTime)
                + " - "
                + bidderName
                + " - "
                + AuctionDisplayFormatter.formatCurrency(amount)
                + " ("
                + status
                + ")");
        if (lines.size() > 10) {
            lines = new ArrayList<>(lines.subList(0, 10));
        }
        bidNotificationList.setItems(FXCollections.observableArrayList(lines));
        currentWinnerLabel.setText("Current winner: "
                + bidderName
                + " - "
                + AuctionDisplayFormatter.formatCurrency(amount)
                + " at "
                + formatBidNotificationTime(bidTime));
    }

    private void startRefreshLoop() {
        // The timer only triggers a background refresh so bid history and timers stay current without blocking the UI.
        refreshTimeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.millis(REFRESH_INTERVAL_MILLIS), event -> refreshViewAsync(false))
        );
        refreshTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        refreshTimeline.play();
    }

    private SettlementState localSettlementState(AuctionSettlement settlement, String currentUserId) {
        if (settlement == null) {
            return new SettlementState("Settlement: N/A", true, true);
        }

        boolean isWinner = settlement.getWinnerBidderId().equals(currentUserId);
        boolean admitDisabled = !isWinner
                || settlement.getStatus() != AuctionSettlementStatus.AWAITING_WINNER_ADMISSION;
        boolean awaitingBuyer = isWinner
                && settlement.getStatus() == AuctionSettlementStatus.AWAITING_BUYER_CONFIRMATION;
        return new SettlementState(
                "Settlement: " + settlement.getDisplaySummary(),
                admitDisabled,
                !awaitingBuyer
        );
    }

    private SettlementState apiSettlementState(SettlementDetail settlement, String currentUserId) {
        if (settlement == null) {
            return new SettlementState("Settlement: N/A", true, true);
        }

        boolean isWinner = settlement.winnerBidderId().equals(currentUserId);
        boolean admitDisabled = !isWinner || !"AWAITING_WINNER_ADMISSION".equals(settlement.status());
        boolean awaitingBuyer = isWinner && "AWAITING_BUYER_CONFIRMATION".equals(settlement.status());
        return new SettlementState(
                "Settlement: " + settlement.displaySummary(),
                admitDisabled,
                !awaitingBuyer
        );
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
            refreshViewAsync(false);
            showAlert(Alert.AlertType.INFORMATION, title, "Settlement was updated.");
        } catch (AuctionApiClient.ApiClientException | IllegalArgumentException | IllegalStateException e) {
            refreshViewAsync(false);
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
        refreshActive = false;
        if (refreshTimeline != null) {
            refreshTimeline.stop();
            refreshTimeline = null;
        }
        refreshExecutor.shutdownNow();
    }

    private void handleRefreshFailure(String message, boolean initialLoad) {
        if (!initialLoad && message.equals(lastRefreshFailureMessage)) {
            return;
        }
        lastRefreshFailureMessage = message;
        showAlert(
                initialLoad ? Alert.AlertType.WARNING : Alert.AlertType.INFORMATION,
                initialLoad ? "Could not load auction" : "Auction view refresh failed",
                message
        );
    }

    private String refreshFailureMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? "Auction details could not be refreshed."
                : current.getMessage();
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

    private record AuctionViewSnapshot(
            boolean missingAuction,
            String itemName,
            String description,
            String status,
            double currentPrice,
            double minimumNextBid,
            String displayEndTime,
            long secondsRemaining,
            List<Bid> bids,
            double requiredDeposit,
            boolean depositConfirmed,
            boolean canBid,
            boolean confirmEntryDisabled,
            String settlementSummary,
            boolean admitResultDisabled,
            boolean confirmReceivedDisabled
    ) {
        private static AuctionViewSnapshot missing() {
            return new AuctionViewSnapshot(
                    true,
                    "",
                    "",
                    "",
                    0.0,
                    0.0,
                    "N/A",
                    0L,
                    List.of(),
                    0.0,
                    false,
                    false,
                    true,
                    "Settlement: N/A",
                    true,
                    true
            );
        }
    }

    private record SettlementState(String summary, boolean admitDisabled, boolean confirmDisabled) {
    }
}
