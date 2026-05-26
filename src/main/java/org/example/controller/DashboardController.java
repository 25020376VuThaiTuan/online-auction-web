package org.example.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
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
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.example.auction.AuctionDepositResult;
import org.example.auction.AuctionRules;
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
import org.example.util.AuctionCatalogFilters;
import org.example.util.AuctionCatalogFilters.FilterRequest;
import org.example.util.AuctionDisplayFormatter;
import org.example.util.BidChartUtils;
import org.example.util.BackgroundExecutorFactory;
import org.example.util.CurrencyInputParser;
import org.example.util.ResponsiveViewSupport;
import org.example.util.SceneNavigator;
import org.example.viewmodel.AuctionEligibilityEntry;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class DashboardController {
    private static final java.time.Duration WALLET_PIN_TRUST_DURATION = java.time.Duration.ofMinutes(120);
    private static final int REFRESH_INTERVAL_MILLIS = 3_000;
    private static final AuctionCatalogFilters.EntryAdapter<AuctionEligibilityEntry> AUCTION_ENTRY_ADAPTER =
            new AuctionCatalogFilters.EntryAdapter<>() {
                @Override
                public String itemId(AuctionEligibilityEntry entry) {
                    return entry.getItemId();
                }

                @Override
                public String itemName(AuctionEligibilityEntry entry) {
                    return entry.getItemName();
                }

                @Override
                public String status(AuctionEligibilityEntry entry) {
                    return entry.getStatus();
                }

                @Override
                public double currentPrice(AuctionEligibilityEntry entry) {
                    return entry.getCurrentPrice();
                }

                @Override
                public long remainingSeconds(AuctionEligibilityEntry entry) {
                    return entry.getRemainingSeconds();
                }
            };

    private final AuctionApiClient apiClient = AuctionApiClient.getInstance();
    private final MarketplaceDashboardService dashboardService = MarketplaceDashboardService.getInstance();
    private final ApplicationSession applicationSession = ApplicationSession.getInstance();
    private final DashboardBidderNameResolver bidderNameResolver =
            new DashboardBidderNameResolver(applicationSession, dashboardService);
    private final ExecutorService refreshExecutor = BackgroundExecutorFactory.newSingleThreadExecutor("dashboard-refresh");
    private final ExecutorService selectionDetailExecutor = BackgroundExecutorFactory.newSingleThreadExecutor("dashboard-selection-refresh");
    private final ExecutorService connectionTestExecutor = BackgroundExecutorFactory.newSingleThreadExecutor("api-connection-test");
    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);
    private final AtomicBoolean refreshPending = new AtomicBoolean(false);

    private Timeline refreshTimeline;
    private volatile boolean refreshActive;
    private String selectedAuctionId;
    private WalletSummary openedWalletSummary;
    private final List<AuctionSettlement> adminSettlementItems = new ArrayList<>();
    private boolean suppressAuctionSelectionRefresh;
    private boolean suppressSellerSelectionRefresh;
    private long auctionDetailRequestId;
    private long sellerDetailRequestId;
    private String lastDashboardRefreshFailureMessage;
    private String lastAuctionDetailFailureMessage;
    private String lastSellerDetailFailureMessage;
    private List<AuctionEligibilityEntry> latestAuctionEntries = List.of();
    private DashboardBidPanelPresenter bidPanelPresenter;
    private DashboardAdminPresenter adminPresenter;

    @FXML
    private TabPane dashboardTabPane;

    @FXML
    private Tab sellerTab;

    @FXML
    private Tab adminTab;

    @FXML
    private Label signedInUserLabel;

    @FXML
    private Button testConnectionButton;

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
    private Label overviewAuctionCountLabel;

    @FXML
    private Label overviewRunningCountLabel;

    @FXML
    private Label overviewEnteredCountLabel;

    @FXML
    private Label overviewEligibleCountLabel;

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
    private TextField walletAccountOpeningBalanceField;

    @FXML
    private TextField walletAccountTopUpAmountField;

    @FXML
    private TableView<AuctionEligibilityEntry> auctionTable;

    @FXML
    private TableColumn<AuctionEligibilityEntry, String> auctionWatchColumn;

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
    private TextField dashboardAuctionSearchField;

    @FXML
    private ChoiceBox<String> dashboardAuctionStatusFilterChoiceBox;

    @FXML
    private ChoiceBox<String> dashboardAuctionSortChoiceBox;

    @FXML
    private CheckBox dashboardAuctionOpenOnlyCheckBox;

    @FXML
    private CheckBox dashboardWatchedOnlyCheckBox;

    @FXML
    private CheckBox dashboardEligibleOnlyCheckBox;

    @FXML
    private Label auctionResultsSummaryLabel;

    @FXML
    private Label selectedAuctionLabel;

    @FXML
    private Label selectedAuctionDepositLabel;

    @FXML
    private Label selectedAuctionTimeRemainingLabel;

    @FXML
    private Label selectedAuctionEndTimeLabel;

    @FXML
    private Label currentWinnerLabel;

    @FXML
    private ListView<String> bidNotificationList;

    @FXML
    private Button confirmAuctionEntryButton;

    @FXML
    private Button watchSelectedAuctionButton;

    @FXML
    private LineChart<String, Number> bidHistoryChart;

    @FXML
    private TextField bidAmountField;

    @FXML
    private Button bidPlusTenButton;

    @FXML
    private Button bidPlusFiftyButton;

    @FXML
    private Button bidPlusHundredButton;

    @FXML
    private TextField autoBidMaxField;

    @FXML
    private TextField autoBidIncrementField;

    @FXML
    private Label bidEntryTimeRemainingLabel;

    @FXML
    private Button placeDashboardBidButton;

    @FXML
    private Button registerAutoBidButton;

    @FXML
    private Button disableAutoBidButton;

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
        BidChartUtils.configureLiveBidChart(bidHistoryChart);
        configureRoleTabs();
        bindCurrentUserFields();
        refreshActive = true;
        refreshViewAsync(true);
        startRefreshLoop();
    }

    @FXML
    private void handleTestConnection() {
        if (!apiClient.isEnabled()) {
            showAlert(
                    Alert.AlertType.INFORMATION,
                    "API not configured",
                    "AUCTION_API_BASE_URL is not set. The app is using local demo data."
            );
            return;
        }

        String previousText = testConnectionButton.getText();
        testConnectionButton.setText("Testing...");
        testConnectionButton.setDisable(true);

        CompletableFuture
                .supplyAsync(apiClient::testConnection, connectionTestExecutor)
                .whenComplete((result, throwable) -> Platform.runLater(() -> {
                    testConnectionButton.setText(previousText);
                    testConnectionButton.setDisable(false);

                    if (throwable == null) {
                        showConnectionSuccess(result);
                    } else {
                        showConnectionFailure(unwrapCompletionException(throwable));
                    }
                }));
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
            WalletSummary summary;
            if (useApi()) {
                summary = apiClient.getWallet(apiToken(), pin);
                refreshApiCurrentUser();
            } else {
                summary = dashboardService.getWallet(currentUser(), pin);
            }
            rememberWalletAuthorization(pin, rememberWalletPinCheckBox.isSelected());
            openedWalletSummary = summary;
            walletPinField.clear();
            refreshWallet(summary);
            refreshViewAsync(false);
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
            refreshViewAsync(false);
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
            refreshViewAsync(false);
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
            showAlert(Alert.AlertType.WARNING, "Account data required", "Fill in account holder name, provider, and reference.");
            return;
        }

        double initialBalance;
        try {
            initialBalance = parseOptionalAmount(walletAccountOpeningBalanceField.getText());
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Invalid opening balance", "Opening balance must be numeric.");
            return;
        }
        if (!Double.isFinite(initialBalance) || initialBalance < 0.0) {
            showAlert(Alert.AlertType.WARNING, "Invalid opening balance", "Opening balance must be zero or greater.");
            return;
        }

        String walletPin = requestWalletPin("Add Wallet Account");
        if (walletPin == null) {
            return;
        }

        try {
            WalletSummary summary = useApi()
                    ? apiClient.addWalletAccount(apiToken(), accountName, provider, reference, initialBalance, false, walletPin)
                    : dashboardService.addWalletAccount(currentUser(), accountName, provider, reference, initialBalance, false, walletPin);
            openedWalletSummary = summary;
            walletAccountNameField.clear();
            walletProviderField.clear();
            walletAccountReferenceField.clear();
            walletAccountOpeningBalanceField.clear();
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
    private void handleTopUpWalletAccount() {
        String accountId = selectedWalletAccountId();
        if (accountId == null) {
            showAlert(Alert.AlertType.WARNING, "Selection required", "Select a wallet account first.");
            return;
        }

        try {
            double amount = parseAmount(walletAccountTopUpAmountField.getText());
            String walletPin = requestWalletPin("Top Up Bank Account");
            if (walletPin == null) {
                return;
            }
            WalletSummary summary = useApi()
                    ? apiClient.topUpWalletAccount(apiToken(), accountId, amount, walletPin)
                    : dashboardService.topUpWalletAccount(currentUser(), accountId, amount, walletPin);
            openedWalletSummary = summary;
            walletAccountTopUpAmountField.clear();
            refreshWallet(summary);
            showAlert(Alert.AlertType.INFORMATION, "Bank account topped up", "Selected bank account balance was increased.");
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Invalid amount", "Amount must be numeric.");
        } catch (AuctionApiClient.ApiClientException | IllegalArgumentException | IllegalStateException e) {
            showAlert(Alert.AlertType.WARNING, "Bank account top-up failed", e.getMessage());
        }
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
    private void handleClearAuctionFilters() {
        dashboardAuctionSearchField.clear();
        dashboardAuctionStatusFilterChoiceBox.setValue(AuctionCatalogFilters.ALL_STATUSES);
        dashboardAuctionSortChoiceBox.setValue(AuctionCatalogFilters.SORT_ENDING_SOON);
        dashboardAuctionOpenOnlyCheckBox.setSelected(false);
        dashboardWatchedOnlyCheckBox.setSelected(false);
        dashboardEligibleOnlyCheckBox.setSelected(false);
        applyAuctionFilters();
    }

    @FXML
    private void handleToggleSelectedAuctionWatch() {
        AuctionEligibilityEntry selected = auctionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Selection required", "Select an auction first.");
            return;
        }

        applicationSession.toggleWatchedAuction(selected.getItemId());
        applyAuctionFilters();
    }

    @FXML
    private void handleWatchVisibleAuctions() {
        auctionTable.getItems().forEach(entry -> applicationSession.watchAuction(entry.getItemId()));
        applyAuctionFilters();
    }

    @FXML
    private void handleClearWatchedAuctions() {
        applicationSession.clearWatchedAuctions();
        applyAuctionFilters();
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
            refreshViewAsync(false);
            showAlert(result.accepted() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING,
                    result.accepted() ? "Deposit locked" : "Deposit not locked",
                    result.message());
        } catch (AuctionApiClient.ApiClientException | IllegalArgumentException | IllegalStateException e) {
            showAlert(Alert.AlertType.WARNING, "Deposit failed", e.getMessage());
        }
    }

    @FXML
    private void handlePlaceBidFromDashboard() {
        AuctionEligibilityEntry selected = selectedAuctionEntry();
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
            refreshViewAsync(false);
            if (!result.accepted()) {
                showAlert(Alert.AlertType.WARNING, "Bid rejected", result.message());
                return;
            }

            addBidActivityNotification(currentUser().getFullName(), amount, LocalDateTime.now(), "accepted");
            readyBidAmountInput(AuctionRules.minimumNextBid(amount), true);
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Invalid bid", "Bid amount must be numeric.");
        } catch (AuctionApiClient.ApiClientException | IllegalArgumentException | IllegalStateException e) {
            showAlert(Alert.AlertType.WARNING, "Bid failed", e.getMessage());
        }
    }

    @FXML
    private void handleAddTenToBid() {
        applyBidIncrement(10.0);
    }

    @FXML
    private void handleAddFiftyToBid() {
        applyBidIncrement(50.0);
    }

    @FXML
    private void handleAddHundredToBid() {
        applyBidIncrement(100.0);
    }

    @FXML
    private void handleRegisterAutoBidFromDashboard() {
        AuctionEligibilityEntry selected = selectedAuctionEntry();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Selection required", "Select an item from the item list first.");
            return;
        }

        try {
            double maxLimit = parseAmount(autoBidMaxField.getText());
            double bidIncrement = parseOptionalAmount(autoBidIncrementField.getText());
            if (bidIncrement < 0.0) {
                showAlert(Alert.AlertType.WARNING, "Invalid auto-bid", "Auto-bid increment must be zero or greater.");
                return;
            }

            String walletPin = requestWalletPin("Enable Auto-bid");
            if (walletPin == null) {
                return;
            }

            boolean saved;
            if (useApi()) {
                apiClient.registerAutoBid(apiToken(), selected.getItemId(), maxLimit, bidIncrement, walletPin);
                saved = true;
            } else {
                saved = dashboardService.registerAutoBidWithDeposit(
                        selected.getItemId(),
                        currentUser(),
                        maxLimit,
                        bidIncrement,
                        walletPin
                );
            }

            refreshViewAsync(false);
            if (!saved) {
                showAlert(Alert.AlertType.WARNING,
                        "Auto-bid not saved",
                        "Confirm entry deposit and make sure available balance covers the auto-bid maximum.");
                return;
            }

            autoBidMaxField.clear();
            autoBidIncrementField.clear();
            addBidPanelMessage("Auto-bid enabled up to " + AuctionDisplayFormatter.formatCurrency(maxLimit));
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Invalid auto-bid", "Auto-bid amounts must be numeric.");
        } catch (AuctionApiClient.ApiClientException | IllegalArgumentException | IllegalStateException e) {
            showAlert(Alert.AlertType.WARNING, "Auto-bid failed", e.getMessage());
        }
    }

    @FXML
    private void handleDisableAutoBidFromDashboard() {
        AuctionEligibilityEntry selected = selectedAuctionEntry();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Selection required", "Select an item from the item list first.");
            return;
        }

        String walletPin = requestWalletPin("Disable Auto-bid");
        if (walletPin == null) {
            return;
        }

        try {
            boolean disabled = useApi()
                    ? apiClient.disableAutoBid(apiToken(), selected.getItemId(), walletPin)
                    : dashboardService.disableAutoBidWithDeposit(selected.getItemId(), currentUser(), walletPin);
            refreshViewAsync(false);
            addBidPanelMessage(disabled ? "Auto-bid disabled" : "No active auto-bid to disable");
        } catch (AuctionApiClient.ApiClientException | IllegalArgumentException | IllegalStateException e) {
            showAlert(Alert.AlertType.WARNING, "Auto-bid disable failed", e.getMessage());
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
            refreshViewAsync(false);
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
            refreshViewAsync(false);
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
            refreshViewAsync(false);
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
            refreshViewAsync(false);
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
        refreshViewAsync(false);
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
        stopRefreshLoop();
        applicationSession.logout();
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
            refreshViewAsync(false);
            showAlert(Alert.AlertType.INFORMATION, title, "Selected item status changed to " + approvalStatus + ".");
        } catch (AuctionApiClient.ApiClientException e) {
            showAlert(Alert.AlertType.WARNING, "Approval update failed", e.getMessage());
        }
    }

    private void configureTables() {
        DashboardTableConfigurator.configureWalletTables(new DashboardTableConfigurator.WalletTables(
                walletTransactionTable,
                walletTransactionTimeColumn,
                walletTransactionTypeColumn,
                walletTransactionAmountColumn,
                walletTransactionBalanceColumn,
                walletTransactionNoteColumn,
                walletAccountTable,
                walletAccountPrimaryColumn,
                walletAccountProviderColumn,
                walletAccountNameColumn,
                walletAccountReferenceColumn,
                walletAccountBalanceColumn
        ));
        DashboardTableConfigurator.configureAuctionTable(new DashboardTableConfigurator.AuctionTableConfig(
                applicationSession,
                auctionTable,
                auctionWatchColumn,
                auctionNameColumn,
                auctionStatusColumn,
                auctionCurrentPriceColumn,
                auctionMinimumBidColumn,
                auctionRequiredDepositColumn,
                auctionAvailableBalanceColumn,
                auctionTimeRemainingColumn,
                auctionEndTimeColumn,
                auctionEligibleColumn,
                () -> suppressAuctionSelectionRefresh,
                this::updateAuctionWatchAction,
                this::clearBidSection,
                this::showSelectedAuctionSummary,
                this::refreshSelectedAuctionDetailAsync,
                itemId -> selectedAuctionId = itemId,
                this::eligibleStyleClass
        ));
        DashboardTableConfigurator.configureSellerTable(new DashboardTableConfigurator.SellerTableConfig(
                sellerItemsTable,
                sellerItemNameColumn,
                sellerItemStatusColumn,
                sellerItemCurrentPriceColumn,
                sellerAuctionStartColumn,
                sellerAuctionEndColumn,
                sellerBidHistoryList,
                () -> suppressSellerSelectionRefresh,
                this::applySellerSelectionState,
                this::refreshSellerSelectionAsync
        ));
        DashboardTableConfigurator.configureAdminTables(new DashboardTableConfigurator.AdminTableConfig(
                userTable,
                adminUsernameColumn,
                adminFullNameColumn,
                adminEmailColumn,
                adminRoleColumn,
                roleChoiceBox,
                pendingItemsTable,
                pendingItemNameColumn,
                pendingSellerColumn,
                pendingStatusColumn
        ));
        DashboardTableConfigurator.configureSellerItemTypeChoice(sellerItemTypeChoiceBox);
        configureAuctionFilters();
    }

    private void configureRoleTabs() {
        User user = currentUser();
        sellerTab.setDisable(!isSeller(user) && !isAdmin(user));
        adminTab.setDisable(!isAdmin(user));
    }

    private void configureAuctionFilters() {
        dashboardAuctionStatusFilterChoiceBox.setItems(FXCollections.observableArrayList(AuctionCatalogFilters.statusOptions()));
        dashboardAuctionStatusFilterChoiceBox.setValue(AuctionCatalogFilters.ALL_STATUSES);

        dashboardAuctionSortChoiceBox.setItems(FXCollections.observableArrayList(AuctionCatalogFilters.sortOptions()));
        dashboardAuctionSortChoiceBox.setValue(AuctionCatalogFilters.SORT_ENDING_SOON);

        dashboardAuctionSearchField.textProperty().addListener((ignored, previous, current) -> applyAuctionFilters());
        dashboardAuctionStatusFilterChoiceBox.getSelectionModel().selectedItemProperty().addListener((ignored, previous, current) -> applyAuctionFilters());
        dashboardAuctionSortChoiceBox.getSelectionModel().selectedItemProperty().addListener((ignored, previous, current) -> applyAuctionFilters());
        dashboardAuctionOpenOnlyCheckBox.selectedProperty().addListener((ignored, previous, current) -> applyAuctionFilters());
        dashboardWatchedOnlyCheckBox.selectedProperty().addListener((ignored, previous, current) -> applyAuctionFilters());
        dashboardEligibleOnlyCheckBox.selectedProperty().addListener((ignored, previous, current) -> applyAuctionFilters());
    }

    private void bindCurrentUserFields() {
        User user = currentUser();
        fullNameField.setText(user.getFullName());
        phoneField.setText(user.getPhoneNumber());
        addressArea.setText(user.getAddress());
        avatarUrlField.setText(user.getAvatarUrl());
    }

    private void refreshViewAsync(boolean initialLoad) {
        if (!refreshActive) {
            return;
        }
        if (!refreshInFlight.compareAndSet(false, true)) {
            refreshPending.set(true);
            return;
        }

        refreshPending.set(false);
        DashboardLoadContext context = captureDashboardLoadContext();
        CompletableFuture
                .supplyAsync(() -> loadDashboardSnapshot(context), refreshExecutor)
                .whenComplete((snapshot, throwable) -> Platform.runLater(() -> {
                    try {
                        if (!refreshActive) {
                            return;
                        }
                        if (throwable != null) {
                            handleDashboardRefreshFailure(refreshFailureMessage(throwable), initialLoad);
                            return;
                        }
                        applyDashboardSnapshot(snapshot);
                        lastDashboardRefreshFailureMessage = null;
                    } finally {
                        refreshInFlight.set(false);
                        if (refreshPending.getAndSet(false) && refreshActive) {
                            refreshViewAsync(false);
                        }
                    }
                }));
    }

    private void refreshAccountSummary(User user) {
        refreshAccountSummary(user, localWalletSnapshot(user));
    }

    private void refreshAccountSummary(User user, WalletSummary walletSnapshot) {
        signedInUserLabel.setText(user.getFullName() + " (" + user.getUsername() + ")");
        roleLabel.setText(user.getRole());
        emailLabel.setText(user.getEmail());
        avatarPreviewLabel.setText(user.getAvatarUrl().isBlank() ? "No avatar selected" : user.getAvatarUrl());
        WalletSummary wallet = dashboardWallet(user, walletSnapshot);

        if (wallet != null) {
            balanceLabel.setText(AuctionDisplayFormatter.formatCurrency(wallet.balance()));
            lockedBalanceLabel.setText(AuctionDisplayFormatter.formatCurrency(wallet.lockedBalance()));
            availableBalanceLabel.setText(AuctionDisplayFormatter.formatCurrency(wallet.availableBalance()));
        } else if (user instanceof Bidder bidder) {
            balanceLabel.setText(AuctionDisplayFormatter.formatCurrency(bidder.getBalance()));
            lockedBalanceLabel.setText(AuctionDisplayFormatter.formatCurrency(bidder.getLockedBalance()));
            availableBalanceLabel.setText(AuctionDisplayFormatter.formatCurrency(bidder.getAvailableBalance()));
        } else {
            balanceLabel.setText(AuctionDisplayFormatter.formatCurrency(0.0));
            lockedBalanceLabel.setText(AuctionDisplayFormatter.formatCurrency(0.0));
            availableBalanceLabel.setText(AuctionDisplayFormatter.formatCurrency(0.0));
        }
    }

    private void refreshWalletSnapshot(User user) {
        refreshWalletSnapshot(user, localWalletSnapshot(user));
    }

    private void refreshWalletSnapshot(User user, WalletSummary walletSnapshot) {
        WalletSummary wallet = openedWalletSummary != null && openedWalletSummary.userId().equals(user.getId())
                ? mergeWalletFinancials(openedWalletSummary, walletWithCurrentFinancials(walletSnapshot, user))
                : null;
        if (wallet != null) {
            openedWalletSummary = wallet;
            refreshWallet(wallet);
            return;
        }

        WalletSummary displayWallet = walletWithCurrentFinancials(walletSnapshot, user);
        if (displayWallet != null) {
            walletBalanceLabel.setText(AuctionDisplayFormatter.formatCurrency(displayWallet.balance()));
            walletLockedLabel.setText(AuctionDisplayFormatter.formatCurrency(displayWallet.lockedBalance()));
            walletAvailableLabel.setText(AuctionDisplayFormatter.formatCurrency(displayWallet.availableBalance()));
        } else if (user instanceof Bidder bidder) {
            walletBalanceLabel.setText(AuctionDisplayFormatter.formatCurrency(bidder.getBalance()));
            walletLockedLabel.setText(AuctionDisplayFormatter.formatCurrency(bidder.getLockedBalance()));
            walletAvailableLabel.setText(AuctionDisplayFormatter.formatCurrency(bidder.getAvailableBalance()));
        } else {
            walletBalanceLabel.setText(AuctionDisplayFormatter.formatCurrency(0.0));
            walletLockedLabel.setText(AuctionDisplayFormatter.formatCurrency(0.0));
            walletAvailableLabel.setText(AuctionDisplayFormatter.formatCurrency(0.0));
        }
        boolean pinSetKnown = displayWallet != null || !useApi();
        boolean pinSet = displayWallet != null ? displayWallet.pinSet() : pinSetKnown && dashboardService.hasWalletPin(user);
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

    private WalletSummary localWalletSnapshot(User user) {
        if (user == null || useApi()) {
            return null;
        }
        try {
            return dashboardService.getWalletSnapshot(user);
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            return null;
        }
    }

    private void refreshWalletPinSetupState(boolean pinSetKnown, boolean pinSet) {
        setWalletPinButton.setDisable(pinSetKnown && pinSet);
    }

    private DashboardLoadContext captureDashboardLoadContext() {
        boolean api = useApi();
        return new DashboardLoadContext(
                api,
                api ? apiToken() : null,
                currentUser()
        );
    }

    private DashboardSnapshot loadDashboardSnapshot(DashboardLoadContext context) {
        AuctionApiClient.CurrentUserSnapshot currentUserSnapshot = context.useApi()
                ? apiClient.getCurrentUserSnapshot(context.apiToken())
                : null;
        User refreshedUser = currentUserSnapshot == null ? null : currentUserSnapshot.user();
        User snapshotUser = refreshedUser != null ? refreshedUser : context.currentUser();
        WalletSummary walletSnapshot = context.useApi()
                ? (currentUserSnapshot == null ? null : currentUserSnapshot.wallet())
                : dashboardService.getWalletSnapshot(snapshotUser);
        List<String> notificationLines = context.useApi()
                ? apiClient.getNotifications(context.apiToken())
                : dashboardService.getNotifications(snapshotUser).stream()
                .map(UserNotification::getDisplayText)
                .toList();
        List<AuctionEligibilityEntry> auctionEntries = context.useApi()
                ? apiClient.getAuctionEligibilityEntries(context.apiToken(), snapshotUser)
                : dashboardService.getAuctionEligibilityEntries(snapshotUser);
        List<Item> sellerItems = loadSellerItems(context, snapshotUser);
        AdminSectionSnapshot adminSection = loadAdminSectionSnapshot(context, snapshotUser);
        return new DashboardSnapshot(refreshedUser, walletSnapshot, notificationLines, auctionEntries, sellerItems, adminSection);
    }

    private List<Item> loadSellerItems(DashboardLoadContext context, User user) {
        if (!isSeller(user) && !isAdmin(user)) {
            return List.of();
        }
        return context.useApi()
                ? apiClient.getSellerItems(context.apiToken())
                : dashboardService.getSellerItems(user);
    }

    private AdminSectionSnapshot loadAdminSectionSnapshot(DashboardLoadContext context, User user) {
        if (!isAdmin(user)) {
            return AdminSectionSnapshot.notAdmin();
        }

        try {
            List<User> users = context.useApi()
                    ? apiClient.getAllUsers(context.apiToken())
                    : dashboardService.getAllUsers();
            List<Item> pendingItems = context.useApi()
                    ? apiClient.getPendingApprovalItems(context.apiToken())
                    : dashboardService.getPendingApprovalItems();
            List<String> settlementLines = new ArrayList<>();
            List<AuctionSettlement> settlements = new ArrayList<>();
            if (context.useApi()) {
                for (AuctionApiClient.SettlementDetail settlement : apiClient.getSettlements(context.apiToken())) {
                    settlementLines.add(settlement.displaySummary());
                    settlements.add(new AuctionSettlement(
                            settlement.itemId(),
                            settlement.itemName(),
                            settlement.sellerId(),
                            settlement.winnerBidderId(),
                            settlement.winningBidAmount(),
                            settlement.depositAmount(),
                            settlement.buyerPremiumAmount(),
                            settlement.totalBuyerDue(),
                            settlement.remainingPaymentDue(),
                            settlement.adminCommission(),
                            settlement.sellerPayout(),
                            LocalDateTime.now()
                    ));
                    AuctionSettlement apiSettlement = settlements.get(settlements.size() - 1);
                    if (!settlement.status().isBlank()) {
                        apiSettlement.setStatus(AuctionSettlementStatus.valueOf(settlement.status()));
                    }
                    apiSettlement.setLockedRemainingPayment(settlement.lockedRemainingPayment());
                    apiSettlement.setSellerReleasedAmount(settlement.sellerReleasedAmount());
                    apiSettlement.setBuyerRefundedAmount(settlement.buyerRefundedAmount());
                }
            } else {
                settlements.addAll(dashboardService.getAllSettlements());
                settlementLines.addAll(settlements.stream().map(AuctionSettlement::getDisplaySummary).toList());
            }
            return AdminSectionSnapshot.success(users, pendingItems, settlementLines, settlements);
        } catch (AuctionApiClient.ApiClientException | IllegalStateException exception) {
            return AdminSectionSnapshot.failure(refreshFailureMessage(exception, "Admin data is temporarily unavailable."));
        }
    }

    private void applyDashboardSnapshot(DashboardSnapshot snapshot) {
        applyRefreshedUser(snapshot.refreshedUser());
        User user = currentUser();
        refreshAccountSummary(user, snapshot.walletSnapshot());
        applyNotifications(snapshot.notificationLines());
        refreshWalletSnapshot(user, snapshot.walletSnapshot());
        applyMarketplaceSummary(snapshot.auctionEntries());
        applyAuctionEntries(snapshot.auctionEntries());
        applySellerItems(snapshot.sellerItems(), user);
        applyAdminSection(snapshot.adminSection(), user);
    }

    private void applyRefreshedUser(User refreshedUser) {
        if (refreshedUser == null) {
            return;
        }
        String previousRole = value(currentUser().getRole());
        applicationSession.replaceCurrentUser(refreshedUser);
        String currentRole = value(refreshedUser.getRole());
        if (!previousRole.equalsIgnoreCase(currentRole)) {
            configureRoleTabs();
            bindCurrentUserFields();
        }
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
            refreshViewAsync(false);
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
        return DashboardWalletSummaries.withCurrentFinancials(wallet, user);
    }

    private WalletSummary dashboardWallet(User user, WalletSummary walletSnapshot) {
        return DashboardWalletSummaries.dashboardWallet(user, walletSnapshot, openedWalletSummary);
    }

    private WalletSummary mergeWalletFinancials(WalletSummary detailWallet, WalletSummary financialWallet) {
        return DashboardWalletSummaries.mergeFinancials(detailWallet, financialWallet);
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

    private void applyMarketplaceSummary(List<AuctionEligibilityEntry> entries) {
        int total = entries.size();
        int live = (int) entries.stream()
                .filter(entry -> AuctionCatalogFilters.isOpenStatus(entry.getStatus()))
                .count();
        int entered = (int) entries.stream()
                .filter(AuctionEligibilityEntry::isDepositConfirmed)
                .count();
        int actionable = (int) entries.stream()
                .filter(entry -> entry.isEligible() || entry.isDepositConfirmed())
                .count();
        overviewAuctionCountLabel.setText(String.valueOf(total));
        overviewRunningCountLabel.setText(String.valueOf(live));
        overviewEnteredCountLabel.setText(String.valueOf(entered));
        overviewEligibleCountLabel.setText(String.valueOf(actionable));
    }

    private void applyAuctionEntries(List<AuctionEligibilityEntry> entries) {
        latestAuctionEntries = entries;
        applyAuctionFilters();
    }

    private void applyAuctionFilters() {
        String desiredSelectionId = selectedAuctionId;
        boolean actionableOnly = dashboardEligibleOnlyCheckBox.isSelected();

        List<AuctionEligibilityEntry> filteredEntries = AuctionCatalogFilters.filterAndSort(
                        latestAuctionEntries,
                        new FilterRequest(
                                dashboardAuctionSearchField.getText(),
                                dashboardAuctionStatusFilterChoiceBox.getValue(),
                                dashboardAuctionSortChoiceBox.getValue(),
                                dashboardAuctionOpenOnlyCheckBox.isSelected(),
                                dashboardWatchedOnlyCheckBox.isSelected()
                        ),
                        AUCTION_ENTRY_ADAPTER,
                        applicationSession::isAuctionWatched
                ).stream()
                .filter(entry -> !actionableOnly || entry.isEligible() || entry.isDepositConfirmed())
                .toList();

        suppressAuctionSelectionRefresh = true;
        try {
            auctionTable.setItems(FXCollections.observableArrayList(filteredEntries));
            auctionTable.getSelectionModel().clearSelection();
            if (desiredSelectionId != null) {
                auctionTable.getItems().stream()
                        .filter(entry -> desiredSelectionId.equals(entry.getItemId()))
                        .findFirst()
                        .ifPresent(entry -> auctionTable.getSelectionModel().select(entry));
            }
        } finally {
            suppressAuctionSelectionRefresh = false;
        }

        updateAuctionResultsSummary(filteredEntries.size(), latestAuctionEntries.size());

        AuctionEligibilityEntry selectedEntry = auctionTable.getSelectionModel().getSelectedItem();
        if (selectedEntry == null) {
            updateAuctionWatchAction(null);
            clearBidSection();
            return;
        }

        selectedAuctionId = selectedEntry.getItemId();
        updateAuctionWatchAction(selectedEntry);
        showSelectedAuctionSummary(selectedEntry, false);
        refreshSelectedAuctionDetailAsync(selectedEntry);
    }

    private void updateAuctionResultsSummary(int visibleCount, int totalCount) {
        if (totalCount == 0) {
            auctionResultsSummaryLabel.setText("No auction sessions are available right now.");
            return;
        }
        if (dashboardWatchedOnlyCheckBox.isSelected() && visibleCount == 0) {
            auctionResultsSummaryLabel.setText("No watched auction sessions match the current filters.");
            return;
        }
        if (visibleCount == totalCount) {
            auctionResultsSummaryLabel.setText(totalCount + " auction sessions available");
            return;
        }
        auctionResultsSummaryLabel.setText("Showing " + visibleCount + " of " + totalCount + " auction sessions");
    }

    private void updateAuctionWatchAction(AuctionEligibilityEntry entry) {
        boolean hasSelection = entry != null;
        watchSelectedAuctionButton.setDisable(!hasSelection);
        watchSelectedAuctionButton.setText(hasSelection && applicationSession.isAuctionWatched(entry.getItemId())
                ? "Unwatch"
                : "Watch");
        auctionTable.refresh();
    }

    private void showSelectedAuctionSummary(AuctionEligibilityEntry entry, boolean clearHistory) {
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
        if (canBid) {
            readyBidAmountInput(entry.getMinimumBid(), clearHistory);
        }
        bidPlusTenButton.setDisable(!canBid);
        bidPlusFiftyButton.setDisable(!canBid);
        bidPlusHundredButton.setDisable(!canBid);
        placeDashboardBidButton.setDisable(!canBid);
        autoBidMaxField.setDisable(!canBid);
        autoBidMaxField.setPromptText("Max " + AuctionDisplayFormatter.formatCurrency(entry.getAvailableBalance()));
        autoBidIncrementField.setDisable(!canBid);
        registerAutoBidButton.setDisable(!canBid);
        disableAutoBidButton.setDisable(!canBid);
        if (clearHistory) {
            bidPanel().clearChart();
            clearBidStatusViews();
            applyBuyerSettlementButtons(SettlementButtonState.disabled());
        }
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
        bidPlusTenButton.setDisable(true);
        bidPlusFiftyButton.setDisable(true);
        bidPlusHundredButton.setDisable(true);
        placeDashboardBidButton.setDisable(true);
        autoBidMaxField.clear();
        autoBidMaxField.setDisable(true);
        autoBidMaxField.setPromptText("Auto-bid max limit");
        autoBidIncrementField.clear();
        autoBidIncrementField.setDisable(true);
        registerAutoBidButton.setDisable(true);
        disableAutoBidButton.setDisable(true);
        confirmAuctionEntryButton.setDisable(true);
        updateAuctionWatchAction(null);
        bidPanel().clearChart();
        clearBidStatusViews();
        applyBuyerSettlementButtons(SettlementButtonState.disabled());
    }

    private void applyNotifications(List<String> lines) {
        notificationList.setItems(FXCollections.observableArrayList(lines));
    }

    private void applyBidHistory(List<Bid> bidHistory) {
        bidPanel().applyBidHistory(bidHistory, bidderNameResolver::displayName);
    }

    private String formatBidNotificationTime(LocalDateTime value) {
        return DashboardFormatters.formatBidNotificationTime(value);
    }

    private void addBidActivityNotification(String bidderName, double amount, LocalDateTime bidTime, String status) {
        bidPanel().addBidActivityNotification(bidderName, amount, bidTime, status);
    }

    private void addBidPanelMessage(String message) {
        bidPanel().addMessage(currentUser().getFullName(), message);
    }

    private void clearBidStatusViews() {
        bidPanel().clearStatusViews();
    }

    private void refreshSelectedAuctionDetailAsync(AuctionEligibilityEntry entry) {
        long requestId = ++auctionDetailRequestId;
        AuctionSelectionLoadContext context = new AuctionSelectionLoadContext(
                requestId,
                useApi(),
                useApi() ? apiToken() : null,
                currentUser().getId(),
                entry.getItemId()
        );
        CompletableFuture
                .supplyAsync(() -> loadAuctionDetailSectionSnapshot(context), selectionDetailExecutor)
                .whenComplete((snapshot, throwable) -> Platform.runLater(() -> {
                    if (!refreshActive || requestId != auctionDetailRequestId) {
                        return;
                    }
                    AuctionEligibilityEntry selected = selectedAuctionEntry();
                    if (selected == null || !selected.getItemId().equals(context.itemId())) {
                        return;
                    }
                    if (throwable != null) {
                        handleAuctionDetailRefreshFailure(refreshFailureMessage(throwable, "Auction details are temporarily unavailable."));
                        return;
                    }
                    applyAuctionDetailSectionSnapshot(snapshot);
                    lastAuctionDetailFailureMessage = null;
                }));
    }

    private AuctionDetailSectionSnapshot loadAuctionDetailSectionSnapshot(AuctionSelectionLoadContext context) {
        return new AuctionDetailSectionSnapshot(
                context.requestId(),
                context.itemId(),
                bidHistory(context.useApi(), context.apiToken(), context.itemId()),
                loadBuyerSettlementButtonState(context.useApi(), context.apiToken(), context.itemId(), context.currentUserId())
        );
    }

    private SettlementButtonState loadBuyerSettlementButtonState(boolean useApi, String apiToken, String itemId, String currentUserId) {
        if (itemId == null || itemId.isBlank()) {
            return SettlementButtonState.disabled();
        }

        String status;
        String winnerId;
        if (useApi) {
            AuctionApiClient.SettlementDetail settlement = apiClient.getSettlement(apiToken, itemId);
            if (settlement == null) {
                return SettlementButtonState.disabled();
            }
            status = settlement.status();
            winnerId = settlement.winnerBidderId();
        } else {
            AuctionSettlement settlement = dashboardService.getSettlement(itemId).orElse(null);
            if (settlement == null) {
                return SettlementButtonState.disabled();
            }
            status = settlement.getStatus().name();
            winnerId = settlement.getWinnerBidderId();
        }

        boolean isWinner = currentUserId.equals(winnerId);
        boolean admitDisabled = !isWinner || !"AWAITING_WINNER_ADMISSION".equals(status);
        boolean confirmDisabled = !isWinner || !"AWAITING_BUYER_CONFIRMATION".equals(status);
        return new SettlementButtonState(admitDisabled, confirmDisabled);
    }

    private void applyAuctionDetailSectionSnapshot(AuctionDetailSectionSnapshot snapshot) {
        applyBidHistory(snapshot.bidHistory());
        applyBuyerSettlementButtons(snapshot.settlementButtonState());
    }

    private void applyBuyerSettlementButtons(SettlementButtonState state) {
        admitDashboardResultButton.setDisable(state.admitDisabled());
        confirmDashboardReceivedButton.setDisable(state.confirmDisabled());
    }

    private void applySellerItems(List<Item> items, User user) {
        if (!isSeller(user) && !isAdmin(user)) {
            sellerItemsTable.setItems(FXCollections.observableArrayList());
            sellerBidHistoryList.setItems(FXCollections.observableArrayList());
            applySellerSelectionState(null, false);
            return;
        }

        Item previousSelection = sellerItemsTable.getSelectionModel().getSelectedItem();
        String previousSelectedItemId = previousSelection == null ? null : previousSelection.getId();
        suppressSellerSelectionRefresh = true;
        try {
            sellerItemsTable.setItems(FXCollections.observableArrayList(items));
            sellerItemsTable.getSelectionModel().clearSelection();
            if (previousSelectedItemId != null) {
                sellerItemsTable.getItems().stream()
                        .filter(item -> previousSelectedItemId.equals(item.getId()))
                        .findFirst()
                        .ifPresent(item -> sellerItemsTable.getSelectionModel().select(item));
            }
        } finally {
            suppressSellerSelectionRefresh = false;
        }

        Item selectedItem = sellerItemsTable.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            sellerBidHistoryList.setItems(FXCollections.observableArrayList());
            applySellerSelectionState(null, false);
            return;
        }

        applySellerSelectionState(selectedItem, false);
        refreshSellerSelectionAsync(selectedItem);
    }

    private void applySellerSelectionState(Item item, boolean canShip) {
        User user = currentUser();
        boolean sellerCanAct = item != null && item.getSellerId() != null && item.getSellerId().equalsIgnoreCase(user.getId());
        boolean hasItem = item != null;
        sellerStartAuctionButton.setDisable(!sellerCanAct);
        sellerFinishAuctionButton.setDisable(!sellerCanAct);
        sellerMarkShippedButton.setDisable(!sellerCanAct || !canShip);
    }

    private void refreshSellerSelectionAsync(Item item) {
        long requestId = ++sellerDetailRequestId;
        SellerSelectionLoadContext context = new SellerSelectionLoadContext(
                requestId,
                useApi(),
                useApi() ? apiToken() : null,
                item.getId()
        );
        CompletableFuture
                .supplyAsync(() -> loadSellerSelectionDetailSnapshot(context), selectionDetailExecutor)
                .whenComplete((snapshot, throwable) -> Platform.runLater(() -> {
                    if (!refreshActive || requestId != sellerDetailRequestId) {
                        return;
                    }
                    Item selectedItem = sellerItemsTable.getSelectionModel().getSelectedItem();
                    if (selectedItem == null || !selectedItem.getId().equals(context.itemId())) {
                        return;
                    }
                    if (throwable != null) {
                        handleSellerDetailRefreshFailure(refreshFailureMessage(throwable, "Seller item details are temporarily unavailable."));
                        return;
                    }
                    applySellerSelectionDetailSnapshot(snapshot);
                    lastSellerDetailFailureMessage = null;
                }));
    }

    private SellerSelectionDetailSnapshot loadSellerSelectionDetailSnapshot(SellerSelectionLoadContext context) {
        List<String> bidHistoryLines = bidHistory(context.useApi(), context.apiToken(), context.itemId()).stream()
                .map(this::formatSellerBidHistoryLine)
                .toList();
        boolean canShip = loadSellerCanShip(context.useApi(), context.apiToken(), context.itemId());
        return new SellerSelectionDetailSnapshot(context.requestId(), context.itemId(), bidHistoryLines, canShip);
    }

    private String formatSellerBidHistoryLine(Bid bid) {
        return DashboardFormatters.formatSellerBidHistoryLine(bid);
    }

    private boolean loadSellerCanShip(boolean useApi, String apiToken, String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        if (useApi) {
            AuctionApiClient.SettlementDetail settlement = apiClient.getSettlement(apiToken, itemId);
            return settlement != null && AuctionSettlementStatus.AWAITING_SELLER_CONFIRMATION.name().equals(settlement.status());
        }
        return dashboardService.getSettlement(itemId)
                .map(settlement -> settlement.getStatus() == AuctionSettlementStatus.AWAITING_SELLER_CONFIRMATION)
                .orElse(false);
    }

    private void applySellerSelectionDetailSnapshot(SellerSelectionDetailSnapshot snapshot) {
        sellerBidHistoryList.setItems(FXCollections.observableArrayList(snapshot.bidHistoryLines()));
        applySellerSelectionState(sellerItemsTable.getSelectionModel().getSelectedItem(), snapshot.canShip());
    }

    private void applyAdminSection(AdminSectionSnapshot adminSection, User user) {
        adminPresenter().apply(
                adminSection.admin(),
                isAdmin(user),
                adminSection.failureMessage(),
                adminSection.users(),
                adminSection.pendingItems(),
                adminSection.settlementLines(),
                adminSection.settlementItems()
        );
    }

    private String walletAuditLine(WalletTransaction transaction) {
        return DashboardFormatters.walletAuditLine(transaction);
    }

    private void handleAdminRefreshFailure(String message) {
        adminPresenter().handleRefreshFailure(message);
    }

    private void handleDashboardRefreshFailure(String message, boolean initialLoad) {
        if (applicationSession.getCurrentUser().filter(this::isAdmin).isPresent()) {
            handleAdminRefreshFailure(message);
        }

        String resolvedMessage = message == null || message.isBlank()
                ? "Dashboard data is temporarily unavailable."
                : message;
        if (!initialLoad || resolvedMessage.equals(lastDashboardRefreshFailureMessage)) {
            lastDashboardRefreshFailureMessage = resolvedMessage;
            return;
        }

        lastDashboardRefreshFailureMessage = resolvedMessage;
        showAlert(
                Alert.AlertType.WARNING,
                "Dashboard opened with partial data",
                resolvedMessage
        );
    }

    private void handleAuctionDetailRefreshFailure(String message) {
        if (message.equals(lastAuctionDetailFailureMessage)) {
            return;
        }
        lastAuctionDetailFailureMessage = message;
        showAlert(Alert.AlertType.WARNING, "Auction details unavailable", message);
    }

    private void handleSellerDetailRefreshFailure(String message) {
        if (message.equals(lastSellerDetailFailureMessage)) {
            return;
        }
        lastSellerDetailFailureMessage = message;
        showAlert(Alert.AlertType.WARNING, "Seller item details unavailable", message);
    }

    private void showConnectionSuccess(AuctionApiClient.ConnectionTestResult result) {
        String serverTime = result.serverTime().isBlank() ? "" : "\nServer time: " + result.serverTime();
        showAlert(
                Alert.AlertType.INFORMATION,
                "API connection successful",
                "Connected to " + result.baseUrl() + "." + serverTime
        );
    }

    private void showConnectionFailure(Throwable failure) {
        if (failure instanceof AuctionApiClient.ApiClientException apiFailure) {
            showAlert(
                    Alert.AlertType.WARNING,
                    AuctionApiClient.isConnectivityFailure(apiFailure) ? "API unavailable" : "API health check failed",
                    apiFailure.getMessage()
            );
            return;
        }

        showAlert(Alert.AlertType.WARNING, "API health check failed", refreshFailureMessage(failure));
    }

    private Throwable unwrapCompletionException(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private String refreshFailureMessage(Throwable throwable) {
        return refreshFailureMessage(throwable, "Dashboard data is temporarily unavailable.");
    }

    private String refreshFailureMessage(Throwable throwable, String fallback) {
        Throwable current = throwable;
        while (current.getCause() != null && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? fallback
                : current.getMessage();
    }

    private DashboardBidPanelPresenter bidPanel() {
        if (bidPanelPresenter == null) {
            bidPanelPresenter = new DashboardBidPanelPresenter(
                    currentWinnerLabel,
                    bidNotificationList,
                    bidHistoryChart
            );
        }
        return bidPanelPresenter;
    }

    private DashboardAdminPresenter adminPresenter() {
        if (adminPresenter == null) {
            adminPresenter = new DashboardAdminPresenter(
                    userTable,
                    pendingItemsTable,
                    adminSettlementList,
                    adminWalletAuditList,
                    adminSettlementItems
            );
        }
        return adminPresenter;
    }

    private void startRefreshLoop() {
        refreshTimeline = new Timeline(new KeyFrame(Duration.millis(REFRESH_INTERVAL_MILLIS), event -> refreshViewAsync(false)));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    private void stopRefreshLoop() {
        refreshActive = false;
        if (refreshTimeline != null) {
            refreshTimeline.stop();
            refreshTimeline = null;
        }
        refreshExecutor.shutdownNow();
        selectionDetailExecutor.shutdownNow();
        connectionTestExecutor.shutdownNow();
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
        return bidHistory(useApi(), useApi() ? apiToken() : null, itemId);
    }

    private List<Bid> bidHistory(boolean useApi, String apiToken, String itemId) {
        return useApi
                ? apiClient.getBidHistory(apiToken, itemId)
                : dashboardService.getBidHistory(itemId);
    }

    private boolean isSeller(User user) {
        return "SELLER".equalsIgnoreCase(user.getRole());
    }

    private boolean isAdmin(User user) {
        return "ADMIN".equalsIgnoreCase(user.getRole());
    }

    private String eligibleStyleClass(String value) {
        return DashboardFormatters.eligibleStyleClass(value);
    }

    private String value(String text) {
        return DashboardFormatters.value(text);
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
        return CurrencyInputParser.parseRequiredAmount(text);
    }

    private double parseOptionalAmount(String text) {
        return CurrencyInputParser.parseOptionalAmount(text);
    }

    private void readyBidAmountInput(double minimumBid, boolean force) {
        if (bidAmountField == null || bidAmountField.isDisabled()) {
            return;
        }

        String currentAmount = value(bidAmountField.getText());
        if (!force && !currentAmount.isBlank()) {
            try {
                if (parseAmount(currentAmount) >= minimumBid) {
                    return;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        bidAmountField.setText(formatAmountInput(minimumBid));
    }

    private void applyBidIncrement(double increment) {
        AuctionEligibilityEntry selected = selectedAuctionEntry();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Selection required", "Select an auction first.");
            return;
        }

        double baseAmount = selected.getMinimumBid();
        try {
            String currentAmount = value(bidAmountField.getText());
            if (!currentAmount.isBlank()) {
                baseAmount = Math.max(baseAmount, parseAmount(currentAmount));
            }
        } catch (NumberFormatException ignored) {
            baseAmount = selected.getMinimumBid();
        }

        bidAmountField.setText(formatAmountInput(baseAmount + increment));
    }

    private AuctionEligibilityEntry selectedAuctionEntry() {
        AuctionEligibilityEntry selected = auctionTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            return selected;
        }
        if (selectedAuctionId == null || selectedAuctionId.isBlank()) {
            return null;
        }
        return auctionTable.getItems().stream()
                .filter(entry -> selectedAuctionId.equals(entry.getItemId()))
                .findFirst()
                .orElse(null);
    }

    private String formatAmountInput(double amount) {
        return CurrencyInputParser.formatAmountInput(amount);
    }

    private String formatDateTime(LocalDateTime value) {
        return DashboardFormatters.formatDateTime(value);
    }

    private void runBuyerSettlementAction(String title, String itemId, Runnable action) {
        if (itemId == null || itemId.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Selection required", "Select an auction first.");
            return;
        }
        try {
            action.run();
            refreshViewAsync(false);
            showAlert(Alert.AlertType.INFORMATION, title, "Settlement was updated.");
        } catch (AuctionApiClient.ApiClientException | IllegalArgumentException | IllegalStateException e) {
            showAlert(Alert.AlertType.WARNING, title + " failed", e.getMessage());
        }
    }

    private void refreshApiCurrentUser() {
        applicationSession.replaceCurrentUser(apiClient.getCurrentUser(apiToken()));
    }

    private record DashboardLoadContext(boolean useApi, String apiToken, User currentUser) {
    }

    private record DashboardSnapshot(
            User refreshedUser,
            WalletSummary walletSnapshot,
            List<String> notificationLines,
            List<AuctionEligibilityEntry> auctionEntries,
            List<Item> sellerItems,
            AdminSectionSnapshot adminSection
    ) {
    }

    private record AdminSectionSnapshot(
            boolean admin,
            List<User> users,
            List<Item> pendingItems,
            List<String> settlementLines,
            List<AuctionSettlement> settlementItems,
            String failureMessage
    ) {
        private static AdminSectionSnapshot notAdmin() {
            return new AdminSectionSnapshot(false, List.of(), List.of(), List.of(), List.of(), null);
        }

        private static AdminSectionSnapshot success(
                List<User> users,
                List<Item> pendingItems,
                List<String> settlementLines,
                List<AuctionSettlement> settlementItems
        ) {
            return new AdminSectionSnapshot(true, users, pendingItems, settlementLines, settlementItems, null);
        }

        private static AdminSectionSnapshot failure(String failureMessage) {
            return new AdminSectionSnapshot(true, List.of(), List.of(), List.of(), List.of(), failureMessage);
        }
    }

    private record AuctionSelectionLoadContext(
            long requestId,
            boolean useApi,
            String apiToken,
            String currentUserId,
            String itemId
    ) {
    }

    private record AuctionDetailSectionSnapshot(
            long requestId,
            String itemId,
            List<Bid> bidHistory,
            SettlementButtonState settlementButtonState
    ) {
    }

    private record SellerSelectionLoadContext(
            long requestId,
            boolean useApi,
            String apiToken,
            String itemId
    ) {
    }

    private record SellerSelectionDetailSnapshot(
            long requestId,
            String itemId,
            List<String> bidHistoryLines,
            boolean canShip
    ) {
    }

    private record SettlementButtonState(boolean admitDisabled, boolean confirmDisabled) {
        private static SettlementButtonState disabled() {
            return new SettlementButtonState(true, true);
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
