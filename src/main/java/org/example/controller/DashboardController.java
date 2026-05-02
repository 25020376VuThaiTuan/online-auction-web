package org.example.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Duration;
import org.example.auction.BidValidationResult;
import org.example.model.ApprovalStatus;
import org.example.model.BankAccount;
import org.example.model.Bid;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.model.User;
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

    private final MarketplaceDashboardService dashboardService = MarketplaceDashboardService.getInstance();
    private final ApplicationSession applicationSession = ApplicationSession.getInstance();

    private Timeline refreshTimeline;
    private String selectedAuctionId;

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
    private TextField fullNameField;

    @FXML
    private TextField phoneField;

    @FXML
    private TextArea addressArea;

    @FXML
    private TextField avatarUrlField;

    @FXML
    private TableView<BankAccount> bankAccountTable;

    @FXML
    private TableColumn<BankAccount, String> bankNameColumn;

    @FXML
    private TableColumn<BankAccount, String> accountHolderColumn;

    @FXML
    private TableColumn<BankAccount, String> accountNumberColumn;

    @FXML
    private TextField bankNameField;

    @FXML
    private TextField accountHolderField;

    @FXML
    private TextField accountNumberField;

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
    private TableColumn<AuctionEligibilityEntry, String> auctionEligibleColumn;

    @FXML
    private Label selectedAuctionLabel;

    @FXML
    private Label selectedAuctionDepositLabel;

    @FXML
    private LineChart<String, Number> bidHistoryChart;

    @FXML
    private TextField bidAmountField;

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
    private TextField sellerStartDelayHoursField;

    @FXML
    private TextField sellerDurationHoursField;

    @FXML
    private TableView<Item> sellerItemsTable;

    @FXML
    private TableColumn<Item, String> sellerItemNameColumn;

    @FXML
    private TableColumn<Item, ApprovalStatus> sellerItemStatusColumn;

    @FXML
    private TableColumn<Item, Double> sellerItemCurrentPriceColumn;

    @FXML
    private ListView<String> sellerBidHistoryList;

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
    public void initialize() {
        if (applicationSession.getCurrentUser().isEmpty()) {
            Platform.runLater(() -> SceneNavigator.switchScene(dashboardTabPane, "/view/Login.fxml", "Online Auction System"));
            return;
        }

        configureTables();
        configureRoleTabs();
        bindCurrentUserFields();
        refreshView();
        startRefreshLoop();
    }

    @FXML
    private void handleSaveProfile() {
        User user = currentUser();
        dashboardService.updateProfile(user, fullNameField.getText(), phoneField.getText(), addressArea.getText());
        refreshAccountSummary(user);
        showAlert(Alert.AlertType.INFORMATION, "Profile updated", "Personal information was saved.");
    }

    @FXML
    private void handleSaveAvatar() {
        User user = currentUser();
        dashboardService.updateAvatar(user, avatarUrlField.getText());
        refreshAccountSummary(user);
        showAlert(Alert.AlertType.INFORMATION, "Avatar updated", "Avatar information was saved.");
    }

    @FXML
    private void handleAddBankAccount() {
        User user = currentUser();
        if (value(bankNameField.getText()).isBlank() || value(accountHolderField.getText()).isBlank() || value(accountNumberField.getText()).isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Missing bank account data", "Fill in bank name, account holder, and account number.");
            return;
        }

        dashboardService.addBankAccount(user, bankNameField.getText(), accountHolderField.getText(), accountNumberField.getText());
        bankNameField.clear();
        accountHolderField.clear();
        accountNumberField.clear();
        refreshBankAccounts(user);
        showAlert(Alert.AlertType.INFORMATION, "Bank account added", "Bank account information was added.");
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
    private void handlePlaceBidFromDashboard() {
        AuctionEligibilityEntry selected = auctionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Selection required", "Select an item from the item list first.");
            return;
        }

        try {
            double amount = Double.parseDouble(value(bidAmountField.getText()));
            BidValidationResult result = dashboardService.placeBidWithDeposit(selected.getItemId(), currentUser(), amount);
            refreshView();
            if (!result.accepted()) {
                showAlert(Alert.AlertType.WARNING, "Bid rejected", result.message());
                return;
            }

            bidAmountField.clear();
            showAlert(Alert.AlertType.INFORMATION, "Bid accepted", result.message());
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Invalid bid", "Bid amount must be numeric.");
        }
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
            int startDelayHours = Integer.parseInt(value(sellerStartDelayHoursField.getText()));
            int durationHours = Integer.parseInt(value(sellerDurationHoursField.getText()));

            if (type == null || itemName.isBlank() || description.isBlank()) {
                showAlert(Alert.AlertType.WARNING, "Missing fields", "Fill in the seller item form first.");
                return;
            }

            dashboardService.addSellerItem(
                    user,
                    type,
                    itemName,
                    description,
                    startingPrice,
                    LocalDateTime.now().plusHours(startDelayHours),
                    LocalDateTime.now().plusHours(startDelayHours + durationHours),
                    sellerExtraTextField.getText(),
                    extraNumber
            );

            sellerItemNameField.clear();
            sellerDescriptionArea.clear();
            sellerStartingPriceField.clear();
            sellerExtraTextField.clear();
            sellerExtraNumberField.clear();
            sellerStartDelayHoursField.clear();
            sellerDurationHoursField.clear();
            refreshSellerData(user);
            showAlert(Alert.AlertType.INFORMATION, "Item submitted", "Seller item is waiting for admin approval.");
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Invalid seller item", "Price and numeric fields must be valid numbers.");
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

        if (!dashboardService.updateUserRole(selectedUser.getId(), selectedRole)) {
            showAlert(Alert.AlertType.WARNING, "Role update failed", "The selected user could not be updated.");
            return;
        }

        if (currentUser().getId().equals(selectedUser.getId())) {
            dashboardService.findUserById(selectedUser.getId()).ifPresent(applicationSession::login);
            bindCurrentUserFields();
            configureRoleTabs();
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
    private void handleLogout() {
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

        dashboardService.updateItemApproval(selectedItem.getId(), approvalStatus);
        refreshAdminData();
        showAlert(Alert.AlertType.INFORMATION, title, "Selected item status changed to " + approvalStatus + ".");
    }

    private void configureTables() {
        bankNameColumn.setCellValueFactory(new PropertyValueFactory<>("bankName"));
        accountHolderColumn.setCellValueFactory(new PropertyValueFactory<>("accountHolder"));
        accountNumberColumn.setCellValueFactory(new PropertyValueFactory<>("maskedAccountNumber"));
        ResponsiveViewSupport.configureResponsiveTable(bankAccountTable);

        auctionNameColumn.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        auctionStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        auctionCurrentPriceColumn.setCellValueFactory(new PropertyValueFactory<>("currentPrice"));
        auctionMinimumBidColumn.setCellValueFactory(new PropertyValueFactory<>("minimumBid"));
        auctionRequiredDepositColumn.setCellValueFactory(new PropertyValueFactory<>("requiredDeposit"));
        auctionAvailableBalanceColumn.setCellValueFactory(new PropertyValueFactory<>("availableBalance"));
        auctionEligibleColumn.setCellValueFactory(new PropertyValueFactory<>("eligibleText"));
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

        sellerItemNameColumn.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        sellerItemStatusColumn.setCellValueFactory(new PropertyValueFactory<>("approvalStatus"));
        sellerItemCurrentPriceColumn.setCellValueFactory(new PropertyValueFactory<>("currentPrice"));
        ResponsiveViewSupport.configureResponsiveTable(sellerItemsTable);
        ResponsiveViewSupport.configureCurrencyColumn(sellerItemCurrentPriceColumn);
        sellerItemsTable.getSelectionModel().selectedItemProperty().addListener((ignored, previous, current) -> refreshSellerBidHistory(current));

        adminUsernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        adminFullNameColumn.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        adminEmailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
        adminRoleColumn.setCellValueFactory(new PropertyValueFactory<>("role"));
        ResponsiveViewSupport.configureResponsiveTable(userTable);
        roleChoiceBox.setItems(FXCollections.observableArrayList("BIDDER", "SELLER", "ADMIN"));

        pendingItemNameColumn.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        pendingSellerColumn.setCellValueFactory(new PropertyValueFactory<>("sellerId"));
        pendingStatusColumn.setCellValueFactory(new PropertyValueFactory<>("approvalStatus"));
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
        User user = currentUser();
        refreshAccountSummary(user);
        refreshBankAccounts(user);
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

    private void refreshAccountSummary(User user) {
        signedInUserLabel.setText(user.getFullName() + " (" + user.getUsername() + ")");
        roleLabel.setText(user.getRole());
        emailLabel.setText(user.getEmail());
        avatarPreviewLabel.setText(user.getAvatarUrl().isBlank() ? "No avatar selected" : user.getAvatarUrl());

        if (user instanceof Bidder bidder) {
            balanceLabel.setText(AuctionDisplayFormatter.formatCurrency(bidder.getBalance()));
            lockedBalanceLabel.setText(AuctionDisplayFormatter.formatCurrency(bidder.getLockedBalance()));
            availableBalanceLabel.setText(AuctionDisplayFormatter.formatCurrency(bidder.getAvailableBalance()));
        } else {
            balanceLabel.setText("N/A");
            lockedBalanceLabel.setText("N/A");
            availableBalanceLabel.setText("N/A");
        }
    }

    private void refreshBankAccounts(User user) {
        bankAccountTable.setItems(FXCollections.observableArrayList(user.getBankAccounts()));
    }

    private void refreshAuctionList(User user) {
        String previousSelectedId = selectedAuctionId;
        auctionTable.setItems(FXCollections.observableArrayList(dashboardService.getAuctionEligibilityEntries(user)));
        if (previousSelectedId != null) {
            auctionTable.getItems().stream()
                    .filter(entry -> previousSelectedId.equals(entry.getItemId()))
                    .findFirst()
                    .ifPresent(entry -> auctionTable.getSelectionModel().select(entry));
        }
    }

    private void refreshBidSection(AuctionEligibilityEntry entry) {
        selectedAuctionLabel.setText(entry.getItemName() + " [" + entry.getStatus() + "]");
        selectedAuctionDepositLabel.setText("Deposit required: " + AuctionDisplayFormatter.formatCurrency(entry.getRequiredDeposit()));
        refreshBidChart(entry.getItemId());
    }

    private void refreshBidChart(String itemId) {
        List<Bid> bidHistory = dashboardService.getBidHistory(itemId);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Bid history");
        for (Bid bid : bidHistory) {
            String label = bid.getBidTime() == null ? "N/A" : CHART_TIME_FORMATTER.format(bid.getBidTime());
            series.getData().add(new XYChart.Data<>(label, bid.getAmount()));
        }
        bidHistoryChart.getData().setAll(series);
    }

    private void refreshSellerData(User user) {
        if (!isSeller(user) && !isAdmin(user)) {
            sellerItemsTable.setItems(FXCollections.observableArrayList());
            sellerBidHistoryList.setItems(FXCollections.observableArrayList());
            return;
        }

        sellerItemsTable.setItems(FXCollections.observableArrayList(dashboardService.getSellerItems(user)));
        refreshSellerBidHistory(sellerItemsTable.getSelectionModel().getSelectedItem());
    }

    private void refreshSellerBidHistory(Item item) {
        if (item == null) {
            sellerBidHistoryList.setItems(FXCollections.observableArrayList());
            return;
        }

        List<String> lines = new ArrayList<>();
        for (Bid bid : dashboardService.getBidHistory(item.getId())) {
            lines.add(bid.getBidderId() + " -> " + AuctionDisplayFormatter.formatCurrency(bid.getAmount())
                    + " at " + (bid.getBidTime() == null ? "N/A" : bid.getBidTime().format(DateTimeFormatter.ofPattern("dd/MM HH:mm:ss"))));
        }
        sellerBidHistoryList.setItems(FXCollections.observableArrayList(lines));
    }

    private void refreshAdminData() {
        if (!isAdmin(currentUser())) {
            userTable.setItems(FXCollections.observableArrayList());
            pendingItemsTable.setItems(FXCollections.observableArrayList());
            return;
        }

        userTable.setItems(FXCollections.observableArrayList(dashboardService.getAllUsers()));
        pendingItemsTable.setItems(FXCollections.observableArrayList(dashboardService.getPendingApprovalItems()));
    }

    private void startRefreshLoop() {
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(2), event -> refreshView()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    private void stopRefreshLoop() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
    }

    private User currentUser() {
        return applicationSession.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("No authenticated user in session."));
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

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
