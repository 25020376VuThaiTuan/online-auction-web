package org.example.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import org.example.client.AuctionApiClient;
import org.example.service.AuctionWorkflowService;
import org.example.state.ApplicationSession;
import org.example.util.BackgroundExecutorFactory;
import org.example.util.ResponsiveViewSupport;
import org.example.util.SceneNavigator;
import org.example.viewmodel.AuctionListEntry;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class AuctionListController {
    private static final int REFRESH_INTERVAL_MILLIS = 3_000;
    private static final String ALL_STATUSES = "All statuses";
    private static final String SORT_ENDING_SOON = "Ending soonest";
    private static final String SORT_PRICE_LOW = "Price low to high";
    private static final String SORT_PRICE_HIGH = "Price high to low";
    private static final String SORT_NAME = "Name A to Z";

    private final AuctionApiClient apiClient = AuctionApiClient.getInstance();
    private final AuctionWorkflowService workflowService = AuctionWorkflowService.getInstance();
    private final ApplicationSession applicationSession = ApplicationSession.getInstance();
    private final ExecutorService refreshExecutor = BackgroundExecutorFactory.newSingleThreadExecutor("auction-list-refresh");
    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);

    private javafx.animation.Timeline refreshTimeline;
    private volatile boolean refreshActive;
    private String lastRefreshFailureMessage;
    private List<AuctionListEntry> latestEntries = List.of();

    @FXML
    private Label welcomeLabel;

    @FXML
    private TableView<AuctionListEntry> auctionTable;

    @FXML
    private TableColumn<AuctionListEntry, String> nameColumn;

    @FXML
    private TableColumn<AuctionListEntry, String> statusColumn;

    @FXML
    private TableColumn<AuctionListEntry, Double> currentPriceColumn;

    @FXML
    private TableColumn<AuctionListEntry, Double> minimumBidColumn;

    @FXML
    private TableColumn<AuctionListEntry, String> timeRemainingColumn;

    @FXML
    private TableColumn<AuctionListEntry, String> endTimeColumn;

    @FXML
    private TextField searchField;

    @FXML
    private ChoiceBox<String> statusFilterChoiceBox;

    @FXML
    private ChoiceBox<String> sortChoiceBox;

    @FXML
    private CheckBox openOnlyCheckBox;

    @FXML
    private Label resultCountLabel;

    @FXML
    private Button openAuctionButton;

    @FXML
    public void initialize() {
        if (applicationSession.getCurrentUser().isEmpty()) {
            Platform.runLater(() -> SceneNavigator.switchScene(auctionTable, "/view/Login.fxml", "Online Auction System"));
            return;
        }

        nameColumn.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        currentPriceColumn.setCellValueFactory(new PropertyValueFactory<>("currentPrice"));
        minimumBidColumn.setCellValueFactory(new PropertyValueFactory<>("minimumNextBid"));
        timeRemainingColumn.setCellValueFactory(new PropertyValueFactory<>("remainingTime"));
        endTimeColumn.setCellValueFactory(new PropertyValueFactory<>("endTimeString"));
        ResponsiveViewSupport.configureResponsiveTable(auctionTable);
        ResponsiveViewSupport.configureCurrencyColumn(currentPriceColumn);
        ResponsiveViewSupport.configureCurrencyColumn(minimumBidColumn);
        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("status-pill", "status-open", "status-running", "status-finished", "status-paid", "status-cancelled");
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                String normalizedStatus = normalizeStatus(item);
                setText(item.replace('_', ' '));
                getStyleClass().add("status-pill");
                getStyleClass().add(statusStyleClass(normalizedStatus));
            }
        });
        auctionTable.getSelectionModel().selectedItemProperty().addListener((ignored, previous, current) ->
                openAuctionButton.setDisable(current == null));
        openAuctionButton.setDisable(true);
        configureMarketplaceControls();

        welcomeLabel.setText("Signed in as: " + applicationSession.getCurrentUserLabel());
        refreshActive = true;
        refreshTableAsync(true);
        startRefreshLoop();
    }

    @FXML
    private void handleClearFilters() {
        searchField.clear();
        statusFilterChoiceBox.setValue(ALL_STATUSES);
        sortChoiceBox.setValue(SORT_ENDING_SOON);
        openOnlyCheckBox.setSelected(false);
        applyAuctionFilters();
    }

    @FXML
    private void handleOpenAuction() {
        AuctionListEntry selectedAuction = auctionTable.getSelectionModel().getSelectedItem();
        if (selectedAuction == null) {
            showAlert("Selection required", "Select an auction first.");
            return;
        }

        applicationSession.setSelectedAuctionId(selectedAuction.getItemId());
        stopRefreshLoop();
        SceneNavigator.switchScene(auctionTable, "/org/example/main_view.fxml", "Auction Detail");
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
        SceneNavigator.switchScene(auctionTable, "/view/Login.fxml", "Online Auction System");
    }

    private void refreshTableAsync(boolean initialLoad) {
        if (!refreshActive || !refreshInFlight.compareAndSet(false, true)) {
            return;
        }

        CompletableFuture
                .supplyAsync(this::loadAuctionListSnapshot, refreshExecutor)
                .whenComplete((snapshot, throwable) -> Platform.runLater(() -> {
                    try {
                        if (!refreshActive) {
                            return;
                        }
                        if (throwable != null) {
                            handleRefreshFailure(refreshFailureMessage(throwable), initialLoad);
                            return;
                        }
                        applyAuctionListSnapshot(snapshot);
                        lastRefreshFailureMessage = null;
                    } finally {
                        refreshInFlight.set(false);
                    }
                }));
    }

    private AuctionListSnapshot loadAuctionListSnapshot() {
        if (!useApi()) {
            workflowService.refreshFromStoreIfChanged();
        }
        List<AuctionListEntry> entries = useApi()
                ? apiClient.getAuctionListEntries(apiToken())
                : workflowService.getAuctionListEntries();
        return new AuctionListSnapshot(applicationSession.getCurrentUserLabel(), entries);
    }

    private void applyAuctionListSnapshot(AuctionListSnapshot snapshot) {
        welcomeLabel.setText("Signed in as: " + snapshot.currentUserLabel());
        latestEntries = snapshot.entries();
        applyAuctionFilters();
    }

    private void configureMarketplaceControls() {
        statusFilterChoiceBox.setItems(FXCollections.observableArrayList(
                ALL_STATUSES,
                "OPEN",
                "RUNNING",
                "FINISHED",
                "PAID",
                "CANCELLED"
        ));
        statusFilterChoiceBox.setValue(ALL_STATUSES);

        sortChoiceBox.setItems(FXCollections.observableArrayList(
                SORT_ENDING_SOON,
                SORT_PRICE_LOW,
                SORT_PRICE_HIGH,
                SORT_NAME
        ));
        sortChoiceBox.setValue(SORT_ENDING_SOON);

        searchField.textProperty().addListener((ignored, previous, current) -> applyAuctionFilters());
        statusFilterChoiceBox.getSelectionModel().selectedItemProperty().addListener((ignored, previous, current) -> applyAuctionFilters());
        sortChoiceBox.getSelectionModel().selectedItemProperty().addListener((ignored, previous, current) -> applyAuctionFilters());
        openOnlyCheckBox.selectedProperty().addListener((ignored, previous, current) -> applyAuctionFilters());
    }

    private void applyAuctionFilters() {
        String selectedId = auctionTable.getSelectionModel().getSelectedItem() == null
                ? null
                : auctionTable.getSelectionModel().getSelectedItem().getItemId();
        String searchText = normalizeText(searchField.getText());
        String selectedStatus = statusFilterChoiceBox.getValue();
        String selectedSort = sortChoiceBox.getValue();
        boolean openOnly = openOnlyCheckBox.isSelected();

        List<AuctionListEntry> filteredEntries = latestEntries.stream()
                .filter(entry -> matchesSearch(entry, searchText))
                .filter(entry -> matchesStatus(entry, selectedStatus))
                .filter(entry -> !openOnly || isOpenStatus(entry.getStatus()))
                .sorted((left, right) -> compareEntries(left, right, selectedSort))
                .toList();

        auctionTable.setItems(FXCollections.observableArrayList(filteredEntries));
        auctionTable.getSelectionModel().clearSelection();
        if (selectedId != null) {
            auctionTable.getItems().stream()
                    .filter(entry -> selectedId.equals(entry.getItemId()))
                    .findFirst()
                    .ifPresent(entry -> auctionTable.getSelectionModel().select(entry));
        }
        openAuctionButton.setDisable(auctionTable.getSelectionModel().getSelectedItem() == null);
        auctionTable.refresh();
        updateResultCountLabel(filteredEntries.size(), latestEntries.size());
    }

    private boolean matchesSearch(AuctionListEntry entry, String searchText) {
        return searchText.isBlank() || normalizeText(entry.getItemName()).contains(searchText);
    }

    private boolean matchesStatus(AuctionListEntry entry, String selectedStatus) {
        return selectedStatus == null
                || ALL_STATUSES.equals(selectedStatus)
                || normalizeStatus(entry.getStatus()).equals(selectedStatus);
    }

    private int compareEntries(AuctionListEntry left, AuctionListEntry right, String selectedSort) {
        if (SORT_PRICE_LOW.equals(selectedSort)) {
            return Double.compare(left.getCurrentPrice(), right.getCurrentPrice());
        }
        if (SORT_PRICE_HIGH.equals(selectedSort)) {
            return Double.compare(right.getCurrentPrice(), left.getCurrentPrice());
        }
        if (SORT_NAME.equals(selectedSort)) {
            return normalizeText(left.getItemName()).compareTo(normalizeText(right.getItemName()));
        }
        return Long.compare(left.getRemainingSeconds(), right.getRemainingSeconds());
    }

    private void updateResultCountLabel(int visibleCount, int totalCount) {
        if (totalCount == 0) {
            resultCountLabel.setText("No auctions are available right now.");
            return;
        }
        if (visibleCount == totalCount) {
            resultCountLabel.setText(totalCount + " auctions available");
            return;
        }
        resultCountLabel.setText("Showing " + visibleCount + " of " + totalCount + " auctions");
    }

    private boolean isOpenStatus(String status) {
        String normalizedStatus = normalizeStatus(status);
        return !"FINISHED".equals(normalizedStatus)
                && !"PAID".equals(normalizedStatus)
                && !"CANCELLED".equals(normalizedStatus);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeStatus(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String statusStyleClass(String status) {
        return switch (status) {
            case "RUNNING" -> "status-running";
            case "FINISHED" -> "status-finished";
            case "PAID" -> "status-paid";
            case "CANCELLED" -> "status-cancelled";
            default -> "status-open";
        };
    }

    private void startRefreshLoop() {
        // The timer only dispatches a background refresh; data loading itself stays off the UI thread.
        refreshTimeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.millis(REFRESH_INTERVAL_MILLIS), event -> refreshTableAsync(false))
        );
        refreshTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        refreshTimeline.play();
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
                initialLoad ? "Could not load auctions" : "Auction list refresh failed",
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
                ? "Auction data could not be refreshed."
                : current.getMessage();
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
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

    private record AuctionListSnapshot(String currentUserLabel, List<AuctionListEntry> entries) {
    }
}
