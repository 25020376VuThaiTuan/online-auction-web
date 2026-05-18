package org.example.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.example.auction.AuctionDepositResult;
import org.example.auction.AuctionSettlement;
import org.example.auction.AuctionSettlementStatus;
import org.example.auction.BidValidationResult;
import org.example.auction.UserNotification;
import org.example.client.AuctionApiClient;
import org.example.client.AuctionApiClient.EntryDepositResponse;
import org.example.model.ApprovalStatus;
import org.example.model.Bid;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.model.User;
import org.example.model.WalletLinkedAccount;
import org.example.model.WalletRecoveryResult;
import org.example.model.WalletSummary;
import org.example.model.WalletTransaction;
import org.example.service.MarketplaceDashboardService;
import org.example.state.ApplicationSession;
import org.example.util.AuctionDisplayFormatter;
import org.example.util.ResponsiveViewSupport;
import org.example.util.SceneNavigator;
import org.example.viewmodel.AuctionEligibilityEntry;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class DashboardController {
    private static final DateTimeFormatter CHART_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DASHBOARD_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final java.time.Duration WALLET_PIN_TRUST_DURATION = java.time.Duration.ofMinutes(120);

    private final AuctionApiClient apiClient = AuctionApiClient.getInstance();
    private final MarketplaceDashboardService dashboardService = MarketplaceDashboardService.getInstance();
    private final ApplicationSession applicationSession = ApplicationSession.getInstance();

    private Timeline refreshTimeline;
    private String selectedAuctionId;
    private WalletSummary openedWalletSummary;
    private final List<AuctionSettlement> adminSettlementItems = new ArrayList<>();
    private String lastAdminRefreshFailureMessage;
    private String lastDashboardRefreshFailureMessage;

    @FXML
    private TabPane dashboardTabPane;

    @FXML
    private Tab sellerTab;

    @FXML
    private Tab adminTab;

    @FXML
    private Label signedInUserLabel;

    @FXML
    private Label roleLabel;

    @FXML
    private Label emailLabel;

    @FXML
    private Label avatarPreviewLabel;

    @FXML
    private Label balanceLabel;

    @FXML
    private Label lockedBalanceLabel;

    @FXML
    private Label availableBalanceLabel;

    @FXML
    private ListView<String> notificationList;

    @FXML
    private TextField fullNameField;

    @FXML
    private TextField phoneField;

    @FXML
    private TextArea addressArea;

    @FXML
    private TextField avatarUrlField;

    @FXML
    private Label walletBalanceLabel;

    @FXML
    private Label walletLockedLabel;

    @FXML
    private Label walletAvailableLabel;

    @FXML
    private Label walletPinStatusLabel;

    @FXML
    private PasswordField walletPinField;

    @FXML
    private CheckBox rememberWalletPinCheckBox;

    @FXML
    private PasswordField newWalletPinField;

    @FXML
    private Button setWalletPinButton;

    @FXML
    private TextField recoveryCodeField;

    @FXML
    private TableView<WalletTransaction> walletTransactionTable;

    @FXML
    private TableColumn<WalletTransaction, String> walletTransactionTimeColumn;

    @FXML
    private TableColumn<WalletTransaction, String> walletTransactionTypeColumn;

    @FXML
    private TableColumn<WalletTransaction, String> walletTransactionAmountColumn;

    @FXML
    private TableColumn<WalletTransaction, String> walletTransactionBalanceColumn;

    @FXML
    private TableColumn<WalletTransaction, String> walletTransactionNoteColumn;

    @FXML
    private TextField walletTransferAmountField;

    @FXML
    private TableView<WalletLinkedAccount> walletAccountTable;

    @FXML
    private TableColumn<WalletLinkedAccount, String> walletAccountPrimaryColumn;

    @FXML
    private TableColumn<WalletLinkedAccount, String> walletAccountProviderColumn;

    @FXML
    private TableColumn<WalletLinkedAccount, String> walletAccountNameColumn;

    @FXML
    private TableColumn<WalletLinkedAccount, String> walletAccountReferenceColumn;

    @FXML
    private TableColumn<WalletLinkedAccount, String> walletAccountBalanceColumn;

    @FXML
    private TextField walletAccountNameField;

    @FXML
    private TextField walletProviderField;

    @FXML
    private TextField walletAccountReferenceField;

    @FXML
    private TableView<AuctionEligibilityEntry> auctionTable;

    @FXML
    private TableColumn<AuctionEligibilityEntry, String> auctionNameColumn;

    @FXML
    private TableColumn<AuctionEligibilityEntry, String> auctionStatusColumn;

    @FXML
    private TableColumn<AuctionEligibilityEntry, Double> auctionCurrentPriceColumn;

    @FXML
    private TableColumn<AuctionEligibilityEntry, Double> auctionMinimumBidColumn;

    @FXML
    private TableColumn<AuctionEligibilityEntry, Double> auctionRequiredDepositColumn;

    @FXML
    private TableColumn<AuctionEligibilityEntry, Double> auctionAvailableBalanceColumn;

    @FXML
    private TableColumn<AuctionEligibilityEntry, String> auctionTimeRemainingColumn;

    @FXML
    private TableColumn<AuctionEligibilityEntry, String> auctionEndTimeColumn;

    @FXML
    private TableColumn<AuctionEligibilityEntry, String> auctionEligibleColumn;

    @FXML
    private Label selectedAuctionLabel;

    @FXML
    private Label selectedAuctionDepositLabel;

    @FXML
    private Label selectedAuctionTimeRemainingLabel;

    @FXML
    private Label selectedAuctionEndTimeLabel;

    @FXML
    private Button confirmAuctionEntryButton;

    @FXML
    private LineChart<String, Number> bidHistoryChart;

    @FXML
    private TextField bidAmountField;

    @FXML
    private Label bidEntryTimeRemainingLabel;

    @FXML
    private Button placeDashboardBidButton;

    @FXML
    private Button admitDashboardResultButton;

    @FXML
    private Button confirmDashboardReceivedButton;

    @FXML
    private ChoiceBox<String> sellerItemTypeChoiceBox;

    @FXML
    private TextField sellerItemNameField;

    @FXML
    private TextArea sellerDescriptionArea;

    @FXML
    private TextField sellerStartingPriceField;

    @FXML
    private TextField sellerExtraTextField;

    @FXML
    private TextField sellerExtraNumberField;

    @FXML
    private TextField sellerPrepareMinutesField;

    @FXML
    private TextField sellerBiddingMinutesField;

    @FXML
    private TableView<Item> sellerItemsTable;

    @FXML
    private TableColumn<Item, String> sellerItemNameColumn;

    @FXML
    private TableColumn<Item, ApprovalStatus> sellerItemStatusColumn;

    @FXML
    private TableColumn<Item, Double> sellerItemCurrentPriceColumn;

    @FXML
    private TableColumn<Item, String> sellerAuctionStartColumn;

    @FXML
    private TableColumn<Item, String> sellerAuctionEndColumn;

    @FXML
    private ListView<String> sellerBidHistoryList;

    @FXML
    private Button sellerStartAuctionButton;

    @FXML
    private Button sellerFinishAuctionButton;

    @FXML
    private Button sellerMarkShippedButton;

    @FXML
    private TableView<User> userTable;

    @FXML
    private TableColumn<User, String> adminUsernameColumn;

    @FXML
    private TableColumn<User, String> adminFullNameColumn;

    @FXML
    private TableColumn<User, String> adminEmailColumn;

    @FXML
    private TableColumn<User, String> adminRoleColumn;

    @FXML
    private ChoiceBox<String> roleChoiceBox;

    @FXML
    private TableView<Item> pendingItemsTable;

    @FXML
    private TableColumn<Item, String> pendingItemNameColumn;

    @FXML
    private TableColumn<Item, String> pendingSellerColumn;

    @FXML
    private TableColumn<Item, ApprovalStatus> pendingStatusColumn;

    @FXML
    private ListView<String> adminSettlementList;

    @FXML
    private ListView<String> adminWalletAuditList;

    @FXML
    public void initialize() {
        if (applicationSession.getCurrentUser().isEmpty()) {
            Platform.runLater(() -> SceneNavigator.switchScene(dashboardTabPane, "/view/Login.fxml", "Online Auction System"));
            return;
        }

        configureTables();
        configureRoleTabs();
        bindCurrentUserFields();
        refreshViewSafely(true);
        startRefreshLoop();
    }

    @FXML
    private void handleSaveProfile() {
        User user = currentUser();
        try {
            if (useApi()) {
                user = apiClient.updateProfile(apiToken(), fullNameField.getText(), phoneField.getText(), addressArea.getText());
                applicationSession.replaceCurrentUser(user);
            } else {
                dashboardService.updateProfile(user, fullNameField.getText(), phoneField.getText(), addressArea.getText());
            }
            refreshAccountSummary(user);
            showAlert(Alert.AlertType.INFORMATION, "Profile updated", "Personal information was saved.");
        } catch (AuctionApiClient.ApiClientException e) {
            showAlert(Alert.AlertType.WARNING, "Profile update failed", e.getMessage());
        }
    }

    @FXML
    private void handleSaveAvatar() {
        User user = currentUser();
        try {
            if (useApi()) {
                user = apiClient.updateAvatar(apiToken(), avatarUrlField.getText());
                applicationSession.replaceCurrentUser(user);
            } else {
                dashboardService.updateAvatar(user, avatarUrlField.getText());
            }
            refreshAccountSummary(user);
            showAlert(Alert.AlertType.INFORMATION, "Avatar updated", "Avatar information was saved.");
        } catch (AuctionApiClient.ApiClientException e) {
            showAlert(Alert.AlertType.WARNING, "Avatar update failed", e.getMessage());
        }
    }

    @FXML
    private void handleOpenWallet() {
        String pin = value(walletPinField.getText());
        if (pin.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Wallet PIN required", "Enter your wallet PIN.");
            return;
        }

        try {
            WalletSummary summary = useApi()
                    ? apiClient.getWallet(apiToken(), pin)
                    : dashboardService.getWallet(currentUser(), pin);
            rememberWalletAuthorization(pin, rememberWalletPinCheckBox.isSelected());
            openedWalletSummary = summary;
            walletPinField.clear();
            refreshWallet(summary);
            refreshAuctionList(currentUser());
        } catch (AuctionApiClient.ApiClientException | IllegalArgumentException | IllegalStateException e) {
            showAlert(Alert.AlertType.WARNING, "Wallet locked", e.getMessage());
        }
    }

    @FXML
    private void handleSetWalletPin() {
        String newPin = value(newWalletPinField.getText());
        if (newPin.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "PIN required", "Enter a new wallet PIN.");
            return;
        }

        try {
            WalletSummary summary;
            if (useApi()) {
                summary = apiClient.setWalletPin(apiToken(), newPin);
                refreshApiCurrentUser();
            } else {
                dashboardService.setWalletPin(currentUser(), newPin);
                summary = dashboardService.getWallet(currentUser(), newPin);
            }
            rememberWalletAuthorization(newPin, rememberWalletPinCheckBox.isSelected());
            openedWalletSummary = summary;
            newWalletPinField.clear();
            refreshWallet(summary);
            refreshAuctionList(currentUser());
            showAlert(Alert.AlertType.INFORMATION, "Wallet PIN saved", "Wallet PIN was saved.");
        } catch (AuctionApiClient.ApiClientException | IllegalArgumentException | IllegalStateException e) {
            showAlert(Alert.AlertType.WARNING, "PIN not saved", e.getMessage());
        }
    }

    @FXML
    private void handleRequestWalletPinRecovery() {
        try {
            WalletRecoveryResult result = useApi()
                    ? apiClient.requestWalletPinRecovery(apiToken())
                    : dashboardService.requestWalletPinRecovery(currentUser());
            showAlert(Alert.AlertType.INFORMATION, "Recovery email sent", result.message());
        } catch (AuctionApiClient.ApiClientException | IllegalArgumentException | IllegalStateException e) {
            showAlert(Alert.AlertType.WARNING, "Recovery failed", e.getMessage());
        }
    }

    @FXML
    private void handleResetWalletPin() {
        String recoveryCode = value(recoveryCodeField.getText());
        String newPin = value(newWalletPinField.getText());
        if (recoveryCode.isBlank() || newPin.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Recovery data required", "Enter the recovery code and a new wallet PIN.");
            return;
        }

        try {
            WalletSummary summary;
            if (useApi()) {
                summary = apiClient.resetWalletPin(apiToken(), recoveryCode, newPin);
                refreshApiCurrentUser();
            } else {
                dashboardService.resetWalletPin(currentUser(), recoveryCode, newPin);
                summary = dashboardService.getWallet(currentUser(), newPin);
            }
            openedWalletSummary = summary;
            recoveryCodeField.clear();
            newWalletPinField.clear();
            refreshWallet(summary);
            refreshAuctionList(currentUser());
            showAlert(Alert.AlertType.INFORMATION, "Wallet PIN reset", "Wallet PIN was reset.");
        } catch (AuctionApiClient.ApiClientException | IllegalArgumentException | IllegalStateException e) {
            showAlert(Alert.AlertType.WARNING, "PIN reset failed", e.getMessage());
        }
    }

    @FXML
    private void handleAddWalletAccount() {
        String accountName = value(walletAccountNameField.getText());
        String provider = value(walletProviderField.getText());
        String reference = value(walletAccountReferenceField.getText());
        if (accountName.isBlank() || provider.isBlank() || reference.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Account data required", "Fill in account name, provider, and reference.");
            return;
        }

        String walletPin = requestWalletPin("Add Wallet Account");
        if (walletPin == null) {
            return;
        }

        try {
            WalletSummary summary = useApi()
                    ? apiClient.addWalletAccount(apiToken(), accountName, provider, reference, false, walletPin)
                    : dashboardService.addWalletAccount(currentUser(), accountName, provider, reference, false, walletPin);
            openedWalletSummary = summary;
            walletAccountNameField.clear();
            walletProviderField.clear();
            walletAccountReferenceField.clear();
            refreshWallet(summary);
            showAlert(Alert.AlertType.INFORMATION, "Account added", "Wallet account was added.");
        } catch (AuctionApiClient.ApiClientException | IllegalArgumentException | IllegalStateException e) {
            showAlert(Alert.AlertType.WARNING, "Account not added", e.getMessage());
        }
    }

    @FXML
    private void handleSetPrimaryWalletAccount() {
        String accountId = selectedWalletAccountId();
        if (accountId == null) {
            showAlert(Alert.AlertType.WARNING, "Selection required", "Select a wallet account first.");
            return;
        }

        String walletPin = requestWalletPin("Set Primary Account");
        if (walletPin == null) {
            return;
        }

        try {
            WalletSummary summary = useApi()
                    ? apiClient.setPrimaryWalletAccount(apiToken(), accountId, walletPin)
                    : dashboardService.setPrimaryWalletAccount(currentUser(), accountId, walletPin);
            openedWalletSummary = summary;
            refreshWallet(summary);
            showAlert(Alert.AlertType.INFORMATION, "Primary account saved", "Selected account is now primary.");
        } catch (AuctionApiClient.ApiClientException | IllegalArgumentException | IllegalStateException e) {
            showAlert(Alert.AlertType.WARNING, "Primary account not saved", e.getMessage());
        }
    }

    @FXML
    private void handleRemoveWalletAccount() {
        String accountId = selectedWalletAccountId();
        if (accountId == null) {
            showAlert(Alert.AlertType.WARNING, "Selection required", "Select a wallet account first.");
            return;
        }
        Alert confirmation = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Remove the selected wallet account?",
                ButtonType.YES,
                ButtonType.NO
        );
        confirmation.setTitle("Remove Account");
        confirmation.setHeaderText(null);
        confirmation.showAndWait();
        if (confirmation.getResult() != ButtonType.YES) {
            return;
        }

        String walletCredential = requestWalletPin("Remove Wallet Account");
        if (walletCredential == null) {
            return;
        }

        try {
            WalletSummary summary = useApi()
                    ? apiClient.removeWalletAccount(apiToken(), accountId, walletCredential)
                    : dashboardService.removeWalletAccount(currentUser(), accountId, walletCredential);
            openedWalletSummary = summary;
            refreshWallet(summary);
            showAlert(Alert.AlertType.INFORMATION, "Account removed", "Wallet account was removed.");
        } catch (AuctionApiClient.ApiClientException | IllegalArgumentException | IllegalStateException e) {
            showAlert(Alert.AlertType.WARNING, "Account not removed", e.getMessage());
        }
    }

    @FXML
    private void handleReceiveWalletMoney() {
        transferWalletMoney(true);
    }

    @FXML
    private void handleSendWalletMoney() {
        transferWalletMoney(false);
    }

    @FXML
    private void handleOpenAuctionDetail() {
        AuctionEligibilityEntry selected = auctionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Selection required", "Select an auction first.");
            return;
        }

        selectedAuctionId = selected.getItemId();
        applicationSession.setSelectedAuctionId(selectedAuctionId);
        stopRefreshLoop();
        SceneNavigator.switchScene(dashboardTabPane, "/org/example/main_view.fxml", "Auction Detail");
    }

    @FXML
    private void handleConfirmAuctionEntry() {
        AuctionEligibilityEntry selected = auctionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Selection required", "Select an auction first.");
            return;
        }
        String walletPin = requestWalletPin("Confirm Entry Deposit");
        if (walletPin == null) {
            return;
        }

        try {
            AuctionDepositResult result;
            if (useApi()) {
                EntryDepositResponse response = apiClient.confirmAuctionEntry(apiToken(), selected.getItemId(), walletPin);
                applicationSession.replaceCurrentUser(response.user());
                result = response.result();
            } else {
                result = dashboardService.confirmAuctionEntry(selected.getItemId(), currentUser(), walletPin);
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
    private void handlePlaceBidFromDashboard() {
        AuctionEligibilityEntry selected = auctionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Selection required", "Select an item from the item list first.");
            return;
        }

        try {
            double amount = parseAmount(bidAmountField.getText());
            String walletPin = requestWalletPin("Place Bid");
            if (walletPin == null) {
                return;
            }
            BidValidationResult result = useApi()
                    ? apiClient.placeBid(apiToken(), selected.getItemId(), amount, walletPin)
                    : dashboardService.placeBidWithDeposit(selected.getItemId(), currentUser(), amount, walletPin);
            refreshView();
            if (!result.accepted()) {
                showAlert(Alert.AlertType.WARNING, "Bid rejected", result.message());
                return;
            }

            bidAmountField.clear();
            showAlert(Alert.AlertType.INFORMATION, "Bid accepted", result.message());
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Invalid bid", "Bid amount must be numeric.");
        } catch (AuctionApiClient.ApiClientException | IllegalArgumentException | IllegalStateException e) {
            showAlert(Alert.AlertType.WARNING, "Bid failed", e.getMessage());
        }
    }

    @FXML
    private void handleAdmitDashboardResult() {
        if (selectedAuctionId == null || selectedAuctionId.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Selection required", "Select an auction first.");
            return;
        }
        String walletPin = requestWalletPin("Admit Result");
        if (walletPin == null) {
            return;
        }
        runBuyerSettlementAction("Result admitted", selectedAuctionId, () -> {
            if (useApi()) {
                apiClient.admitWinnerResult(apiToken(), selectedAuctionId, walletPin);
                refreshApiCurrentUser();
            } else {
                dashboardService.admitWinnerResult(selectedAuctionId, currentUser(), walletPin);
            }
        });
    }

    @FXML
    private void handleConfirmDashboardReceived() {
        if (selectedAuctionId == null || selectedAuctionId.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Selection required", "Select an auction first.");
            return;
        }
        String walletPin = requestWalletPin("Confirm Received");
        if (walletPin == null) {
            return;
        }
        runBuyerSettlementAction("Payment confirmed", selectedAuctionId, () -> {
            if (useApi()) {
                apiClient.confirmGoodsReceived(apiToken(), selectedAuctionId, walletPin);
                refreshApiCurrentUser();
            } else {
                dashboardService.confirmGoodsReceived(selectedAuctionId, currentUser(), walletPin);
            }
        });
    }

    @FXML
    private void handleAddSellerItem() {
        User user = currentUser();
        if (!isSeller(user) && !isAdmin(user)) {
            showAlert(Alert.AlertType.WARNING, "Role required", "Only seller or admin accounts can add items.");
            return;
        }

        try {
            String type = sellerItemTypeChoiceBox.getValue();
            String itemName = value(sellerItemNameField.getText());
            String description = value(sellerDescriptionArea.getText());
            double startingPrice = Double.parseDouble(value(sellerStartingPriceField.getText()));
            int extraNumber = Integer.parseInt(value(sellerExtraNumberField.getText()));
            int prepareMinutes = Integer.parseInt(value(sellerPrepareMinutesField.getText()));
            int biddingMinutes = Integer.parseInt(value(sellerBiddingMinutesField.getText()));

            if (type == null || itemName.isBlank() || description.isBlank()) {
                showAlert(Alert.AlertType.WARNING, "Missing fields", "Fill in the seller item form first.");
                return;
            }
            if (startingPrice <= 0.0 || prepareMinutes < 0 || biddingMinutes <= 0) {
                showAlert(Alert.AlertType.WARNING, "Invalid auction session",
                        "Starting price must be positive, prepare time cannot be negative, and bidding time must be greater than zero.");
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startTime = now.plusMinutes(prepareMinutes);
            LocalDateTime endTime = startTime.plusMinutes(biddingMinutes);

            Item savedItem;
            if (useApi()) {
                savedItem = apiClient.addSellerItem(
                        apiToken(),
                        type,
                        itemName,
                        description,
                        startingPrice,
                        startTime,
                        endTime,
                        sellerExtraTextField.getText(),
                        extraNumber
                );
            } else {
                savedItem = dashboardService.addSellerItem(
                        user,
                        type,
                        itemName,
                        description,
                        startingPrice,
                        startTime,
                        endTime,
                        sellerExtraTextField.getText(),
                        extraNumber
                );
            }

            if (savedItem == null) {
                showAlert(Alert.AlertType.WARNING, "Item save failed", "The item was not saved. Check the API server log for the database error.");
                return;
            }

            sellerItemNameField.clear();
            sellerDescriptionArea.clear();
            sellerStartingPriceField.clear();
            sellerExtraTextField.clear();
            sellerExtraNumberField.clear();
            sellerPrepareMinutesField.clear();
            sellerBiddingMinutesField.clear();
            refreshSellerData(user);
            refreshAdminData();
            showAlert(Alert.AlertType.INFORMATION, "Auction session created", "Auction session is waiting for admin approval.");
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Invalid seller item", "Price and numeric fields must be valid numbers.");
        } catch (AuctionApiClient.ApiClientException e) {
            showAlert(Alert.AlertType.WARNING, "Item save failed", e.getMessage());
        }
    }

    @FXML
    private void handleStartSellerAuction() {
        Item selectedItem = sellerItemsTable.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            showAlert(Alert.AlertType.WARNING, "Selection required", "Select a seller item first.");
            return;
        }

        try {
            if (useApi()) {
                apiClient.startAuction(apiToken(), selectedItem.getId());
            } else if (!dashboardService.startAuction(currentUser(), selectedItem.getId())) {
                showAlert(Alert.AlertType.WARNING, "Auction not started", "This auction could not be started.");
                return;
            }
            refreshView();
            showAlert(Alert.AlertType.INFORMATION, "Auction started", "Seller auction is now running.");
        } catch (AuctionApiClient.ApiClientException e) {
            showAlert(Alert.AlertType.WARNING, "Auction not started", e.getMessage());
        }
    }

    @FXML
    private void handleFinishSellerAuction() {
        Item selectedItem = sellerItemsTable.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            showAlert(Alert.AlertType.WARNING, "Selection required", "Select a seller item first.");
            return;
        }

        try {
            if (useApi()) {
                apiClient.finishAuction(apiToken(), selectedItem.getId());
            } else if (!dashboardService.finishAuction(currentUser(), selectedItem.getId())) {
                showAlert(Alert.AlertType.WARNING, "Auction not finished", "This auction could not be finished.");
                return;
            }
            refreshView();
            showAlert(Alert.AlertType.INFORMATION, "Auction finished", "Auction is finished and locked.");
        } catch (AuctionApiClient.ApiClientException e) {
            showAlert(Alert.AlertType.WARNING, "Auction not finished", e.getMessage());
        }
    }

    @FXML
    private void handleSellerMarkShipped() {
        Item selectedItem = sellerItemsTable.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            showAlert(Alert.AlertType.WARNING, "Selection required", "Select a seller item first.");
            return;
        }
        String walletPin = requestWalletPin("Confirm Sent");
        if (walletPin == null) {
            return;
        }

        try {
            if (useApi()) {
                apiClient.markGoodsShipped(apiToken(), selectedItem.getId(), walletPin);
                refreshApiCurrentUser();
            } else {
                dashboardService.markGoodsShipped(selectedItem.getId(), currentUser(), walletPin);
            }
            refreshView();
            showAlert(Alert.AlertType.INFORMATION, "Item sent confirmed", "Buyer payment is locked until the buyer confirms receipt.");
        } catch (AuctionApiClient.ApiClientException | IllegalArgumentException | IllegalStateException e) {
            showAlert(Alert.AlertType.WARNING, "Item sent not confirmed", e.getMessage());
        }
    }

    @FXML
    private void handleUpdateRole() {
        User selectedUser = userTable.getSelectionModel().getSelectedItem();
        String selectedRole = roleChoiceBox.getValue();
        if (selectedUser == null || selectedRole == null) {
            showAlert(Alert.AlertType.WARNING, "Selection required", "Choose a user and a role.");
            return;
        }

        try {
            if (useApi()) {
                User updatedUser = apiClient.updateUserRole(apiToken(), selectedUser.getId(), selectedRole);
                if (updatedUser == null) {
                    showAlert(Alert.AlertType.WARNING, "Role update failed", "The selected user could not be updated.");
                    return;
                }
                if (currentUser().getId().equals(updatedUser.getId())) {
                    applicationSession.replaceCurrentUser(updatedUser);
                    bindCurrentUserFields();
                    configureRoleTabs();
                }
            } else if (!dashboardService.updateUserRole(selectedUser.getId(), selectedRole)) {
                showAlert(Alert.AlertType.WARNING, "Role update failed", "The selected user could not be updated.");
                return;
            } else if (currentUser().getId().equals(selectedUser.getId())) {
                dashboardService.findUserById(selectedUser.getId()).ifPresent(applicationSession::replaceCurrentUser);
                bindCurrentUserFields();
                configureRoleTabs();
            }
        } catch (AuctionApiClient.ApiClientException e) {
            showAlert(Alert.AlertType.WARNING, "Role update failed", e.getMessage());
            return;
        }
        refreshAdminData();
        showAlert(Alert.AlertType.INFORMATION, "Role updated", "User role was updated.");
    }

    @FXML
    private void handleApproveItem() {
        updateSelectedPendingItem(ApprovalStatus.APPROVED, "Item approved");
    }

    @FXML
    private void handleRejectItem() {
        updateSelectedPendingItem(ApprovalStatus.REJECTED, "Item rejected");
    }

    @FXML
    private void handleLoadWalletAudit() {
        User selectedUser = userTable.getSelectionModel().getSelectedItem();
        if (selectedUser == null) {
            showAlert(Alert.AlertType.WARNING, "Selection required", "Select a user first.");
            return;
        }
        try {
            List<WalletTransaction> transactions = useApi()
                    ? apiClient.getWalletAuditTransactions(apiToken(), selectedUser.getId())
                    : dashboardService.getWalletAuditTransactions(currentUser(), selectedUser.getId());
            List<String> lines = transactions.stream()
                    .map(this::walletAuditLine)
                    .toList();
            adminWalletAuditList.setItems(FXCollections.observableArrayList(
                    lines.isEmpty() ? List.of("No wallet transactions.") : lines
            ));
        } catch (AuctionApiClient.ApiClientException | IllegalArgumentException | IllegalStateException e) {
            showAlert(Alert.AlertType.WARNING, "Wallet audit failed", e.getMessage());
        }
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
        SceneNavigator.switchScene(dashboardTabPane, "/view/Login.fxml", "Online Auction System");
    }

    private void updateSelectedPendingItem(ApprovalStatus approvalStatus, String title) {
        Item selectedItem = pendingItemsTable.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            showAlert(Alert.AlertType.WARNING, "Selection required", "Select a pending item first.");
            return;
        }

        try {
            if (useApi()) {
                apiClient.updateItemApproval(apiToken(), selectedItem.getId(), approvalStatus);
            } else {
                dashboardService.updateItemApproval(selectedItem.getId(), approvalStatus);
            }
            refreshAdminData();
            showAlert(Alert.AlertType.INFORMATION, title, "Selected item status changed to " + approvalStatus + ".");
        } catch (AuctionApiClient.ApiClientException e) {
            showAlert(Alert.AlertType.WARNING, "Approval update failed", e.getMessage());
        }
    }

    private void configureTables() {
        walletTransactionTimeColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(formatDateTime(cellData.getValue().createdAt())));
        walletTransactionTypeColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().transactionType().replace('_', ' ')));
        walletTransactionAmountColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(AuctionDisplayFormatter.formatCurrency(cellData.getValue().amount())));
        walletTransactionBalanceColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(AuctionDisplayFormatter.formatCurrency(cellData.getValue().balanceAfter())));
        walletTransactionNoteColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().note()));
        ResponsiveViewSupport.configureResponsiveTable(walletTransactionTable);

        walletAccountPrimaryColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().primary() ? "Yes" : ""));
        walletAccountProviderColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().providerName()));
        walletAccountNameColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().accountName()));
        walletAccountReferenceColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().maskedReference()));
        walletAccountBalanceColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(AuctionDisplayFormatter.formatCurrency(cellData.getValue().balance())));
        ResponsiveViewSupport.configureResponsiveTable(walletAccountTable);

        auctionNameColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getItemName()));
        auctionStatusColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getStatus()));
        auctionCurrentPriceColumn.setCellValueFactory(cellData ->
                new SimpleObjectProperty<>(cellData.getValue().getCurrentPrice()));
        auctionMinimumBidColumn.setCellValueFactory(cellData ->
                new SimpleObjectProperty<>(cellData.getValue().getMinimumBid()));
        auctionRequiredDepositColumn.setCellValueFactory(cellData ->
                new SimpleObjectProperty<>(cellData.getValue().getRequiredDeposit()));
        auctionAvailableBalanceColumn.setCellValueFactory(cellData ->
                new SimpleObjectProperty<>(cellData.getValue().getAvailableBalance()));
        auctionTimeRemainingColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getRemainingTime()));
        auctionEndTimeColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getEndTimeString()));
        auctionEligibleColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getEligibleText()));
        ResponsiveViewSupport.configureResponsiveTable(auctionTable);
        ResponsiveViewSupport.configureCurrencyColumn(auctionCurrentPriceColumn);
        ResponsiveViewSupport.configureCurrencyColumn(auctionMinimumBidColumn);
        ResponsiveViewSupport.configureCurrencyColumn(auctionRequiredDepositColumn);
        ResponsiveViewSupport.configureCurrencyColumn(auctionAvailableBalanceColumn);
        auctionTable.getSelectionModel().selectedItemProperty().addListener((ignored, previous, current) -> {
            if (current == null) {
                return;
            }
            selectedAuctionId = current.getItemId();
            refreshBidSection(current);
        });

        sellerItemNameColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getItemName()));
        sellerItemStatusColumn.setCellValueFactory(cellData ->
                new SimpleObjectProperty<>(cellData.getValue().getApprovalStatus()));
        sellerItemCurrentPriceColumn.setCellValueFactory(cellData ->
                new SimpleObjectProperty<>(cellData.getValue().getCurrentPrice()));
        sellerAuctionStartColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(formatDateTime(cellData.getValue().getStartTime())));
        sellerAuctionEndColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(formatDateTime(cellData.getValue().getEndTime())));
        ResponsiveViewSupport.configureResponsiveTable(sellerItemsTable);
        ResponsiveViewSupport.configureCurrencyColumn(sellerItemCurrentPriceColumn);
        sellerItemsTable.getSelectionModel().selectedItemProperty().addListener((ignored, previous, current) -> {
            refreshSellerBidHistory(current);
            refreshSellerButtons();
        });

        adminUsernameColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getUsername()));
        adminFullNameColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getFullName()));
        adminEmailColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getEmail()));
        adminRoleColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getRole()));
        ResponsiveViewSupport.configureResponsiveTable(userTable);
        roleChoiceBox.setItems(FXCollections.observableArrayList("BIDDER", "SELLER", "ADMIN"));

        pendingItemNameColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getItemName()));
        pendingSellerColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getSellerId()));
        pendingStatusColumn.setCellValueFactory(cellData ->
                new SimpleObjectProperty<>(cellData.getValue().getApprovalStatus()));
        ResponsiveViewSupport.configureResponsiveTable(pendingItemsTable);

        sellerItemTypeChoiceBox.setItems(FXCollections.observableArrayList("electronics", "art", "vehicle"));
        if (!sellerItemTypeChoiceBox.getItems().isEmpty()) {
            sellerItemTypeChoiceBox.setValue(sellerItemTypeChoiceBox.getItems().get(0));
        }
    }

    private void configureRoleTabs() {
        User user = currentUser();
        sellerTab.setDisable(!isSeller(user) && !isAdmin(user));
        adminTab.setDisable(!isAdmin(user));
    }

    private void bindCurrentUserFields() {
        User user = currentUser();
        fullNameField.setText(user.getFullName());
        phoneField.setText(user.getPhoneNumber());
        addressArea.setText(user.getAddress());
        avatarUrlField.setText(user.getAvatarUrl());
    }

    private void refreshView() {
        refreshApiUserSnapshot();
        User user = currentUser();
        refreshAccountSummary(user);
        refreshNotifications(user);
        refreshWalletSnapshot(user);
        refreshAuctionList(user);
        refreshSellerData(user);
        refreshAdminData();
        if (selectedAuctionId != null) {
            auctionTable.getItems().stream()
                    .filter(entry -> selectedAuctionId.equals(entry.getItemId()))
                    .findFirst()
                    .ifPresent(this::refreshBidSection);
        }
    }

    private void refreshViewSafely(boolean initialLoad) {
        try {
            refreshView();
            lastDashboardRefreshFailureMessage = null;
        } catch (RuntimeException exception) {
            handleDashboardRefreshFailure(exception, initialLoad);
        }
    }

    private void refreshAccountSummary(User user) {
        signedInUserLabel.setText(user.getFullName() + " (" + user.getUsername() + ")");
        roleLabel.setText(user.getRole());
        emailLabel.setText(user.getEmail());
        avatarPreviewLabel.setText(user.getAvatarUrl().isBlank() ? "No avatar selected" : user.getAvatarUrl());
        WalletSummary wallet = openedWalletSummary != null && openedWalletSummary.userId().equals(user.getId())
                ? walletWithCurrentFinancials(openedWalletSummary, user)
                : null;

        if (user instanceof Bidder bidder) {
            balanceLabel.setText(AuctionDisplayFormatter.formatCurrency(bidder.getBalance()));
            lockedBalanceLabel.setText(AuctionDisplayFormatter.formatCurrency(bidder.getLockedBalance()));
            availableBalanceLabel.setText(AuctionDisplayFormatter.formatCurrency(bidder.getAvailableBalance()));
        } else if (wallet != null) {
            balanceLabel.setText(AuctionDisplayFormatter.formatCurrency(wallet.balance()));
            lockedBalanceLabel.setText(AuctionDisplayFormatter.formatCurrency(wallet.lockedBalance()));
            availableBalanceLabel.setText(AuctionDisplayFormatter.formatCurrency(wallet.availableBalance()));
        } else {
            balanceLabel.setText(AuctionDisplayFormatter.formatCurrency(0.0));
            lockedBalanceLabel.setText(AuctionDisplayFormatter.formatCurrency(0.0));
            availableBalanceLabel.setText(AuctionDisplayFormatter.formatCurrency(0.0));
        }
    }

    private void refreshWalletSnapshot(User user) {
        WalletSummary wallet = openedWalletSummary != null && openedWalletSummary.userId().equals(user.getId())
                ? walletWithCurrentFinancials(openedWalletSummary, user)
                : null;
        if (wallet != null) {
            openedWalletSummary = wallet;
            refreshWallet(wallet);
            return;
        }

        if (user instanceof Bidder bidder) {
            walletBalanceLabel.setText(AuctionDisplayFormatter.formatCurrency(bidder.getBalance()));
            walletLockedLabel.setText(AuctionDisplayFormatter.formatCurrency(bidder.getLockedBalance()));
            walletAvailableLabel.setText(AuctionDisplayFormatter.formatCurrency(bidder.getAvailableBalance()));
        } else {
            walletBalanceLabel.setText(AuctionDisplayFormatter.formatCurrency(0.0));
            walletLockedLabel.setText(AuctionDisplayFormatter.formatCurrency(0.0));
            walletAvailableLabel.setText(AuctionDisplayFormatter.formatCurrency(0.0));
        }
        boolean pinSetKnown = !useApi();
        boolean pinSet = pinSetKnown && dashboardService.hasWalletPin(user);
        walletPinStatusLabel.setText(pinSetKnown
                ? (pinSet ? "Set" : "Not set")
                : "Open wallet to verify");
        refreshWalletPinSetupState(pinSetKnown, pinSet);
        walletTransactionTable.setItems(FXCollections.observableArrayList());
        walletAccountTable.setItems(FXCollections.observableArrayList());
    }

    private void refreshWallet(WalletSummary wallet) {
        WalletLinkedAccount previousAccount = walletAccountTable.getSelectionModel().getSelectedItem();
        applyWalletSummaryToAccountBalance(wallet);
        walletBalanceLabel.setText(AuctionDisplayFormatter.formatCurrency(wallet.balance()));
        walletLockedLabel.setText(AuctionDisplayFormatter.formatCurrency(wallet.lockedBalance()));
        walletAvailableLabel.setText(AuctionDisplayFormatter.formatCurrency(wallet.availableBalance()));
        walletPinStatusLabel.setText(wallet.pinSet() ? "Set" : "Not set");
        refreshWalletPinSetupState(true, wallet.pinSet());
        walletTransactionTable.setItems(FXCollections.observableArrayList(wallet.transactions()));
        walletAccountTable.setItems(FXCollections.observableArrayList(wallet.linkedAccounts()));
        if (!wallet.linkedAccounts().isEmpty()) {
            wallet.linkedAccounts().stream()
                    .filter(account -> previousAccount != null && account.id().equals(previousAccount.id()))
                    .findFirst()
                    .ifPresentOrElse(
                            account -> walletAccountTable.getSelectionModel().select(account),
                            () -> walletAccountTable.getSelectionModel().select(0)
                    );
        }
    }

    private void refreshWalletPinSetupState(boolean pinSetKnown, boolean pinSet) {
        setWalletPinButton.setDisable(pinSetKnown && pinSet);
    }

    private void transferWalletMoney(boolean receiving) {
        String accountId = selectedWalletAccountId();
        if (accountId == null) {
            showAlert(Alert.AlertType.WARNING, "Selection required", "Select a wallet account first.");
            return;
        }

        try {
            double amount = parseAmount(walletTransferAmountField.getText());
            String walletPin = requestWalletPin(receiving ? "Receive Money" : "Send Money");
            if (walletPin == null) {
                return;
            }
            WalletSummary summary = receiving
                    ? (useApi()
                            ? apiClient.receiveWalletMoney(apiToken(), accountId, amount, walletPin)
                            : dashboardService.receiveWalletMoney(currentUser(), accountId, amount, walletPin))
                    : (useApi()
                            ? apiClient.sendWalletMoney(apiToken(), accountId, amount, walletPin)
                            : dashboardService.sendWalletMoney(currentUser(), accountId, amount, walletPin));
            if (useApi()) {
                refreshApiCurrentUser();
            }
            openedWalletSummary = summary;
            walletTransferAmountField.clear();
            refreshWallet(summary);
            refreshAccountSummary(currentUser());
            refreshAuctionList(currentUser());
            showAlert(Alert.AlertType.INFORMATION,
                    receiving ? "Money received" : "Money sent",
                    receiving ? "Wallet balance was increased." : "Wallet balance was decreased.");
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Invalid amount", "Amount must be numeric.");
        } catch (AuctionApiClient.ApiClientException | IllegalArgumentException | IllegalStateException e) {
            showAlert(Alert.AlertType.WARNING, receiving ? "Receive failed" : "Send failed", e.getMessage());
        }
    }

    private String selectedWalletAccountId() {
        if (openedWalletSummary == null || openedWalletSummary.linkedAccounts().isEmpty()) {
            return null;
        }
        WalletLinkedAccount selected = walletAccountTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return openedWalletSummary.linkedAccounts().stream()
                    .filter(WalletLinkedAccount::primary)
                    .findFirst()
                    .map(WalletLinkedAccount::id)
                    .orElse(null);
        }
        return selected.id();
    }

    private WalletSummary walletWithCurrentFinancials(WalletSummary wallet, User user) {
        if (wallet == null || user == null || !wallet.userId().equals(user.getId()) || !(user instanceof Bidder bidder)) {
            return wallet;
        }
        return new WalletSummary(
                wallet.userId(),
                bidder.getBalance(),
                bidder.getLockedBalance(),
                bidder.getAvailableBalance(),
                wallet.pinSet(),
                wallet.linkedAccounts(),
                wallet.transactions()
        );
    }

    private void applyWalletSummaryToAccountBalance(WalletSummary wallet) {
        User user = currentUser();
        if (wallet == null || !wallet.userId().equals(user.getId())) {
            return;
        }
        balanceLabel.setText(AuctionDisplayFormatter.formatCurrency(wallet.balance()));
        lockedBalanceLabel.setText(AuctionDisplayFormatter.formatCurrency(wallet.lockedBalance()));
        availableBalanceLabel.setText(AuctionDisplayFormatter.formatCurrency(wallet.availableBalance()));
        if (user instanceof Bidder bidder) {
            bidder.setBalance(wallet.balance());
        }
    }

    private void refreshAuctionList(User user) {
        String previousSelectedId = selectedAuctionId;
        List<AuctionEligibilityEntry> entries = useApi()
                ? apiClient.getAuctionEligibilityEntries(apiToken(), user)
                : dashboardService.getAuctionEligibilityEntries(user);
        auctionTable.setItems(FXCollections.observableArrayList(entries));
        if (previousSelectedId != null) {
            auctionTable.getItems().stream()
                    .filter(entry -> previousSelectedId.equals(entry.getItemId()))
                    .findFirst()
                    .ifPresent(entry -> auctionTable.getSelectionModel().select(entry));
        }
        if (auctionTable.getSelectionModel().getSelectedItem() == null) {
            confirmAuctionEntryButton.setDisable(true);
            clearBidSection();
        }
    }

    private void refreshBidSection(AuctionEligibilityEntry entry) {
        selectedAuctionLabel.setText(entry.getItemName() + " [" + entry.getStatus().replace('_', ' ') + "]");
        selectedAuctionDepositLabel.setText("Deposit required: " + AuctionDisplayFormatter.formatCurrency(entry.getRequiredDeposit())
                + " - " + entry.getEligibleText());
        selectedAuctionTimeRemainingLabel.setText("Time left: " + entry.getRemainingTime());
        bidEntryTimeRemainingLabel.setText("Time remaining: " + entry.getRemainingTime());
        selectedAuctionEndTimeLabel.setText("Ends at: " + entry.getEndTimeString());
        confirmAuctionEntryButton.setDisable(entry.isDepositConfirmed() || !entry.isEligible());
        boolean canBid = entry.isDepositConfirmed() && "RUNNING".equalsIgnoreCase(entry.getStatus());
        bidAmountField.setDisable(!canBid);
        bidAmountField.setPromptText("Min " + AuctionDisplayFormatter.formatCurrency(entry.getMinimumBid()));
        placeDashboardBidButton.setDisable(!canBid);
        refreshBidChart(entry.getItemId());
        refreshBuyerSettlementButtons(entry.getItemId());
    }

    private void clearBidSection() {
        selectedAuctionId = null;
        selectedAuctionLabel.setText("Select an auction from Auction List");
        selectedAuctionDepositLabel.setText("Deposit required: N/A");
        selectedAuctionTimeRemainingLabel.setText("Time left: N/A");
        bidEntryTimeRemainingLabel.setText("Time remaining: N/A");
        selectedAuctionEndTimeLabel.setText("Ends at: N/A");
        bidAmountField.clear();
        bidAmountField.setDisable(true);
        placeDashboardBidButton.setDisable(true);
        bidHistoryChart.getData().clear();
        refreshBuyerSettlementButtons(null);
    }

    private void refreshNotifications(User user) {
        List<String> lines;
        if (useApi()) {
            lines = apiClient.getNotifications(apiToken());
        } else {
            lines = dashboardService.getNotifications(user).stream()
                    .map(UserNotification::getDisplayText)
                    .toList();
        }
        notificationList.setItems(FXCollections.observableArrayList(lines));
        showNewAuctionCompletionPopups(lines);
    }

    private void showNewAuctionCompletionPopups(List<String> notificationLines) {
        List<String> popupLines = notificationLines.stream()
                .filter(this::isAuctionCompletionNotification)
                .filter(applicationSession::rememberNotificationPopup)
                .toList();
        if (popupLines.isEmpty()) {
            return;
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Auction update");
        alert.setHeaderText("Auction session update");
        alert.setContentText(String.join(System.lineSeparator() + System.lineSeparator(), popupLines));
        alert.show();
    }

    private boolean isAuctionCompletionNotification(String line) {
        String normalized = value(line).toLowerCase();
        return normalized.contains("auction finished")
                || normalized.contains("buyer admitted result")
                || normalized.contains("auction result ready")
                || normalized.contains("you have won this session");
    }

    private void refreshBidChart(String itemId) {
        List<Bid> bidHistory = bidHistory(itemId);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Bid history");
        for (Bid bid : bidHistory) {
            String label = bid.getBidTime() == null ? "N/A" : CHART_TIME_FORMATTER.format(bid.getBidTime());
            series.getData().add(new XYChart.Data<>(label, bid.getAmount()));
        }
        bidHistoryChart.getData().clear();
        bidHistoryChart.getData().add(series);
    }

    private void refreshSellerData(User user) {
        if (!isSeller(user) && !isAdmin(user)) {
            sellerItemsTable.setItems(FXCollections.observableArrayList());
            sellerBidHistoryList.setItems(FXCollections.observableArrayList());
            sellerStartAuctionButton.setDisable(true);
            sellerFinishAuctionButton.setDisable(true);
            sellerMarkShippedButton.setDisable(true);
            return;
        }

        Item previousSelection = sellerItemsTable.getSelectionModel().getSelectedItem();
        String previousSelectedItemId = previousSelection == null ? null : previousSelection.getId();
        List<Item> items = useApi()
                ? apiClient.getSellerItems(apiToken())
                : dashboardService.getSellerItems(user);
        sellerItemsTable.setItems(FXCollections.observableArrayList(items));
        if (previousSelectedItemId != null) {
            sellerItemsTable.getItems().stream()
                    .filter(item -> previousSelectedItemId.equals(item.getId()))
                    .findFirst()
                    .ifPresent(item -> sellerItemsTable.getSelectionModel().select(item));
        }
        refreshSellerBidHistory(sellerItemsTable.getSelectionModel().getSelectedItem());
        refreshSellerButtons();
    }

    private void refreshSellerBidHistory(Item item) {
        if (item == null) {
            sellerBidHistoryList.setItems(FXCollections.observableArrayList());
            return;
        }

        List<String> lines = new ArrayList<>();
        for (Bid bid : bidHistory(item.getId())) {
            lines.add(bid.getBidderId() + " -> " + AuctionDisplayFormatter.formatCurrency(bid.getAmount())
                    + " at " + (bid.getBidTime() == null ? "N/A" : bid.getBidTime().format(DateTimeFormatter.ofPattern("dd/MM HH:mm:ss"))));
        }
        sellerBidHistoryList.setItems(FXCollections.observableArrayList(lines));
    }

    private void refreshAdminData() {
        if (!isAdmin(currentUser())) {
            userTable.setItems(FXCollections.observableArrayList());
            pendingItemsTable.setItems(FXCollections.observableArrayList());
            adminSettlementItems.clear();
            adminSettlementList.setItems(FXCollections.observableArrayList());
            adminWalletAuditList.setItems(FXCollections.observableArrayList());
            lastAdminRefreshFailureMessage = null;
            return;
        }

        try {
            List<User> users = useApi()
                    ? apiClient.getAllUsers(apiToken())
                    : dashboardService.getAllUsers();
            List<Item> pendingItems = useApi()
                    ? apiClient.getPendingApprovalItems(apiToken())
                    : dashboardService.getPendingApprovalItems();
            userTable.setItems(FXCollections.observableArrayList(users));
            pendingItemsTable.setItems(FXCollections.observableArrayList(pendingItems));
            refreshAdminSettlements();
            if (lastAdminRefreshFailureMessage != null) {
                adminWalletAuditList.setItems(FXCollections.observableArrayList());
                lastAdminRefreshFailureMessage = null;
            }
        } catch (AuctionApiClient.ApiClientException | IllegalStateException e) {
            handleAdminRefreshFailure(e);
        }
    }

    private String walletAuditLine(WalletTransaction transaction) {
        return formatDateTime(transaction.createdAt())
                + " - "
                + transaction.userId()
                + " - "
                + transaction.transactionType().replace('_', ' ')
                + " - "
                + AuctionDisplayFormatter.formatCurrency(transaction.amount())
                + " - balance "
                + AuctionDisplayFormatter.formatCurrency(transaction.balanceAfter())
                + (transaction.note() == null || transaction.note().isBlank() ? "" : " - " + transaction.note());
    }

    private void refreshSellerButtons() {
        boolean sellerCanAct = isSeller(currentUser()) || isAdmin(currentUser());
        Item selectedItem = sellerItemsTable.getSelectionModel().getSelectedItem();
        boolean hasItem = selectedItem != null;
        sellerStartAuctionButton.setDisable(!sellerCanAct || !hasItem);
        sellerFinishAuctionButton.setDisable(!sellerCanAct || !hasItem);
        boolean canShip = sellerCanAct && hasItem && isAwaitingSellerConfirmation(selectedItem.getId());
        sellerMarkShippedButton.setDisable(!canShip);
    }

    private boolean isAwaitingSellerConfirmation(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }

        if (useApi()) {
            AuctionApiClient.SettlementDetail settlement = apiClient.getSettlement(apiToken(), itemId);
            return settlement != null && AuctionSettlementStatus.AWAITING_SELLER_CONFIRMATION.name().equals(settlement.status());
        }

        return dashboardService.getSettlement(itemId)
                .map(settlement -> settlement.getStatus() == AuctionSettlementStatus.AWAITING_SELLER_CONFIRMATION)
                .orElse(false);
    }

    private void refreshBuyerSettlementButtons(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            admitDashboardResultButton.setDisable(true);
            confirmDashboardReceivedButton.setDisable(true);
            return;
        }

        String currentUserId = currentUser().getId();
        String status;
        String winnerId;
        if (useApi()) {
            AuctionApiClient.SettlementDetail settlement = apiClient.getSettlement(apiToken(), itemId);
            if (settlement == null) {
                admitDashboardResultButton.setDisable(true);
                confirmDashboardReceivedButton.setDisable(true);
                return;
            }
            status = settlement.status();
            winnerId = settlement.winnerBidderId();
        } else {
            AuctionSettlement settlement = dashboardService.getSettlement(itemId).orElse(null);
            if (settlement == null) {
                admitDashboardResultButton.setDisable(true);
                confirmDashboardReceivedButton.setDisable(true);
                return;
            }
            status = settlement.getStatus().name();
            winnerId = settlement.getWinnerBidderId();
        }

        boolean isWinner = currentUserId.equals(winnerId);
        admitDashboardResultButton.setDisable(!isWinner || !"AWAITING_WINNER_ADMISSION".equals(status));
        boolean awaitingBuyer = isWinner && "AWAITING_BUYER_CONFIRMATION".equals(status);
        confirmDashboardReceivedButton.setDisable(!awaitingBuyer);
    }

    private void refreshAdminSettlements() {
        if (!isAdmin(currentUser())) {
            adminSettlementItems.clear();
            adminSettlementList.setItems(FXCollections.observableArrayList());
            return;
        }

        List<String> lines = new ArrayList<>();
        adminSettlementItems.clear();
        if (useApi()) {
            for (AuctionApiClient.SettlementDetail settlement : apiClient.getSettlements(apiToken())) {
                lines.add(settlement.displaySummary());
                adminSettlementItems.add(new AuctionSettlement(
                        settlement.itemId(),
                        settlement.itemName(),
                        "",
                        settlement.winnerBidderId(),
                        settlement.winningBidAmount(),
                        settlement.depositAmount(),
                        settlement.buyerPremiumAmount(),
                        settlement.totalBuyerDue(),
                        settlement.remainingPaymentDue(),
                        0.0,
                        0.0,
                        LocalDateTime.now()
                ));
            }
        } else {
            adminSettlementItems.addAll(dashboardService.getAllSettlements());
            lines.addAll(adminSettlementItems.stream().map(AuctionSettlement::getDisplaySummary).toList());
        }
        adminSettlementList.setItems(FXCollections.observableArrayList(lines));
    }

    private void handleAdminRefreshFailure(RuntimeException exception) {
        String message = exception == null || exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Admin data is temporarily unavailable."
                : exception.getMessage();
        lastAdminRefreshFailureMessage = message;
        userTable.setItems(FXCollections.observableArrayList());
        pendingItemsTable.setItems(FXCollections.observableArrayList());
        adminSettlementItems.clear();
        adminSettlementList.setItems(FXCollections.observableArrayList("Admin data unavailable."));
        adminWalletAuditList.setItems(FXCollections.observableArrayList(message));
    }

    private void handleDashboardRefreshFailure(RuntimeException exception, boolean initialLoad) {
        if (isAdmin(currentUser())) {
            handleAdminRefreshFailure(exception);
        }

        String message = exception == null || exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Dashboard data is temporarily unavailable."
                : exception.getMessage();
        if (!initialLoad || message.equals(lastDashboardRefreshFailureMessage)) {
            lastDashboardRefreshFailureMessage = message;
            return;
        }

        lastDashboardRefreshFailureMessage = message;
        Platform.runLater(() -> showAlert(
                Alert.AlertType.WARNING,
                "Dashboard opened with partial data",
                message
        ));
    }

    private void startRefreshLoop() {
        stopRefreshLoop();
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> refreshViewSafely(false)));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    private void stopRefreshLoop() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
            refreshTimeline = null;
        }
    }

    private User currentUser() {
        return applicationSession.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("No authenticated user in session."));
    }

    private boolean useApi() {
        return apiClient.isEnabled() && applicationSession.getApiToken().isPresent();
    }

    private String apiToken() {
        return applicationSession.getApiToken()
                .orElseThrow(() -> new IllegalStateException("No API token in session."));
    }

    private List<Bid> bidHistory(String itemId) {
        return useApi()
                ? apiClient.getBidHistory(apiToken(), itemId)
                : dashboardService.getBidHistory(itemId);
    }

    private boolean isSeller(User user) {
        return "SELLER".equalsIgnoreCase(user.getRole());
    }

    private boolean isAdmin(User user) {
        return "ADMIN".equalsIgnoreCase(user.getRole());
    }

    private String value(String text) {
        return text == null ? "" : text.trim();
    }

    private String requestWalletPin(String title) {
        String userId = currentUser().getId();
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
        rememberPin.setSelected(rememberWalletPinCheckBox != null && rememberWalletPinCheckBox.isSelected());
        dialog.getDialogPane().setContent(new VBox(10.0, pinField, rememberPin));
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(button -> button == ButtonType.OK ? value(pinField.getText()) : null);
        Platform.runLater(pinField::requestFocus);
        String pin = dialog.showAndWait().orElse(null);
        if (pin == null) {
            return null;
        }
        if (pin.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Wallet PIN required", "Enter your wallet PIN.");
            return null;
        }
        return rememberPin.isSelected() ? walletAuthorizationCredential(pin) : pin;
    }

    private void rememberWalletAuthorization(String pin, boolean remember) {
        if (!remember) {
            return;
        }
        walletAuthorizationCredential(pin);
    }

    private String walletAuthorizationCredential(String pin) {
        try {
            var authorization = useApi()
                    ? apiClient.authorizeWallet(apiToken(), pin)
                    : dashboardService.authorizeWallet(currentUser(), pin, WALLET_PIN_TRUST_DURATION);
            applicationSession.trustWalletAuthorization(currentUser().getId(), authorization.token(), authorization.expiresAt());
            return authorization.token();
        } catch (AuctionApiClient.ApiClientException | IllegalArgumentException | IllegalStateException e) {
            showAlert(Alert.AlertType.WARNING, "Wallet authorization failed", e.getMessage());
            return pin;
        }
    }

    private double parseAmount(String text) {
        String normalized = value(text).replace("$", "").replace(",", "");
        if (normalized.isBlank()) {
            throw new NumberFormatException("Amount is blank.");
        }
        return Double.parseDouble(normalized);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "N/A" : DASHBOARD_DATE_TIME_FORMATTER.format(value);
    }

    private void runBuyerSettlementAction(String title, String itemId, Runnable action) {
        if (itemId == null || itemId.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Selection required", "Select an auction first.");
            return;
        }
        try {
            action.run();
            refreshView();
            showAlert(Alert.AlertType.INFORMATION, title, "Settlement was updated.");
        } catch (AuctionApiClient.ApiClientException | IllegalArgumentException | IllegalStateException e) {
            showAlert(Alert.AlertType.WARNING, title + " failed", e.getMessage());
        }
    }

    private void refreshApiCurrentUser() {
        applicationSession.replaceCurrentUser(apiClient.getCurrentUser(apiToken()));
    }

    private void refreshApiUserSnapshot() {
        if (!useApi()) {
            return;
        }

        String previousRole = value(currentUser().getRole());
        refreshApiCurrentUser();
        String currentRole = value(currentUser().getRole());
        if (!previousRole.equalsIgnoreCase(currentRole)) {
            configureRoleTabs();
            bindCurrentUserFields();
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
