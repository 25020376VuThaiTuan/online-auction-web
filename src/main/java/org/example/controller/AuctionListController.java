package org.example.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import org.example.client.AuctionApiClient;
import org.example.service.AuctionWorkflowService;
import org.example.state.ApplicationSession;
import org.example.util.AuctionCatalogFilters;
import org.example.util.AuctionCatalogFilters.FilterRequest;
import org.example.util.BackgroundExecutorFactory;
import org.example.util.ResponsiveViewSupport;
import org.example.util.SceneNavigator;
import org.example.viewmodel.AuctionListEntry;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class AuctionListController {
    private static final PseudoClass ACTIVE_AUCTION_PSEUDO_CLASS = PseudoClass.getPseudoClass("active-auction");
    private static final int REFRESH_INTERVAL_MILLIS = 3_000;
    private static final AuctionCatalogFilters.EntryAdapter<AuctionListEntry> AUCTION_LIST_ENTRY_ADAPTER =
            new AuctionCatalogFilters.EntryAdapter<>() {
                @Override
                public String itemId(AuctionListEntry entry) {
                    return entry.getItemId();
                }

                @Override
                public String itemName(AuctionListEntry entry) {
                    return entry.getItemName();
                }

                @Override
                public String status(AuctionListEntry entry) {
                    return entry.getStatus();
                }

                @Override
                public double currentPrice(AuctionListEntry entry) {
                    return entry.getCurrentPrice();
                }

                @Override
                public long remainingSeconds(AuctionListEntry entry) {
                    return entry.getRemainingSeconds();
                }
            };

    private final AuctionApiClient apiClient = AuctionApiClient.getInstance();
    private final AuctionWorkflowService workflowService = AuctionWorkflowService.getInstance();
    private final ApplicationSession applicationSession = ApplicationSession.getInstance();
    private final ExecutorService refreshExecutor = BackgroundExecutorFactory.newSingleThreadExecutor("auction-list-refresh");
    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);

    private javafx.animation.Timeline refreshTimeline;
    private volatile boolean refreshActive;
    private boolean suppressAuctionSelectionRefresh;
    private String selectedAuctionId;
    private String lastRefreshFailureMessage;
    private List<AuctionListEntry> latestEntries = List.of();

    @FXML
    private Label welcomeLabel;

    @FXML
    private TableView<AuctionListEntry> auctionTable;

    @FXML
    private TableColumn<AuctionListEntry, String> watchColumn;

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
    private CheckBox watchedOnlyCheckBox;

    @FXML
    private Label resultCountLabel;

    @FXML
    private Button toggleWatchButton;

    @FXML
    private Button openAuctionButton;

    @FXML
    public void initialize() {
        if (applicationSession.getCurrentUser().isEmpty()) {
            Platform.runLater(() -> SceneNavigator.switchScene(auctionTable, "/view/Login.fxml", "Online Auction System"));
            return;
        }

        watchColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                applicationSession.isAuctionWatched(cellData.getValue().getItemId()) ? "Watching" : ""
        ));
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
                String normalizedStatus = AuctionCatalogFilters.normalizeStatus(item);
                setText(item.replace('_', ' '));
                getStyleClass().add("status-pill");
                getStyleClass().add(AuctionCatalogFilters.statusStyleClass(normalizedStatus));
            }
        });
        watchColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("watch-pill", "watch-active");
                if (empty || item == null || item.isBlank()) {
                    setText(null);
                    return;
                }
                setText(item);
                getStyleClass().add("watch-pill");
                getStyleClass().add("watch-active");
            }
        });
        auctionTable.setRowFactory(table -> new TableRow<>() {
            @Override
            protected void updateItem(AuctionListEntry item, boolean empty) {
                super.updateItem(item, empty);
                pseudoClassStateChanged(
                        ACTIVE_AUCTION_PSEUDO_CLASS,
                        !empty
                                && item != null
                                && item.getItemId().equals(selectedAuctionId)
                );
            }
        });
        auctionTable.getSelectionModel().selectedItemProperty().addListener((ignored, previous, current) -> {
            if (!suppressAuctionSelectionRefresh) {
                selectedAuctionId = current == null ? null : current.getItemId();
            }
            updateAuctionSelectionActions();
            auctionTable.refresh();
        });
        auctionTable.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY
                    && event.getClickCount() == 2
                    && auctionTable.getSelectionModel().getSelectedItem() != null) {
                handleOpenAuction();
            }
        });
        updateAuctionSelectionActions();
        configureMarketplaceControls();

        welcomeLabel.setText("Signed in as: " + applicationSession.getCurrentUserLabel());
        refreshActive = true;
        refreshTableAsync(true);
        startRefreshLoop();
    }

    @FXML
    private void handleClearFilters() {
        searchField.clear();
        statusFilterChoiceBox.setValue(AuctionCatalogFilters.ALL_STATUSES);
        sortChoiceBox.setValue(AuctionCatalogFilters.SORT_ENDING_SOON);
        openOnlyCheckBox.setSelected(false);
        watchedOnlyCheckBox.setSelected(false);
        applyAuctionFilters();
    }

    @FXML
    private void handleToggleWatch() {
        AuctionListEntry selectedAuction = auctionTable.getSelectionModel().getSelectedItem();
        if (selectedAuction == null) {
            showAlert("Selection required", "Select an auction first.");
            return;
        }

        applicationSession.toggleWatchedAuction(selectedAuction.getItemId());
        applyAuctionFilters();
    }

    @FXML
    private void handleWatchVisible() {
        auctionTable.getItems().forEach(entry -> applicationSession.watchAuction(entry.getItemId()));
        applyAuctionFilters();
    }

    @FXML
    private void handleClearWatched() {
        applicationSession.clearWatchedAuctions();
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
        statusFilterChoiceBox.setItems(FXCollections.observableArrayList(AuctionCatalogFilters.statusOptions()));
        statusFilterChoiceBox.setValue(AuctionCatalogFilters.ALL_STATUSES);

        sortChoiceBox.setItems(FXCollections.observableArrayList(AuctionCatalogFilters.sortOptions()));
        sortChoiceBox.setValue(AuctionCatalogFilters.SORT_ENDING_SOON);

        searchField.textProperty().addListener((ignored, previous, current) -> applyAuctionFilters());
        statusFilterChoiceBox.getSelectionModel().selectedItemProperty().addListener((ignored, previous, current) -> applyAuctionFilters());
        sortChoiceBox.getSelectionModel().selectedItemProperty().addListener((ignored, previous, current) -> applyAuctionFilters());
        openOnlyCheckBox.selectedProperty().addListener((ignored, previous, current) -> applyAuctionFilters());
        watchedOnlyCheckBox.selectedProperty().addListener((ignored, previous, current) -> applyAuctionFilters());
    }

    private void applyAuctionFilters() {
        AuctionListEntry currentSelection = auctionTable.getSelectionModel().getSelectedItem();
        String selectedId = selectedAuctionId != null
                ? selectedAuctionId
                : currentSelection == null
                ? null
                : currentSelection.getItemId();
        List<AuctionListEntry> filteredEntries = AuctionCatalogFilters.filterAndSort(
                latestEntries,
                new FilterRequest(
                        searchField.getText(),
                        statusFilterChoiceBox.getValue(),
                        sortChoiceBox.getValue(),
                        openOnlyCheckBox.isSelected(),
                        watchedOnlyCheckBox.isSelected()
                ),
                AUCTION_LIST_ENTRY_ADAPTER,
                applicationSession::isAuctionWatched
        );

        suppressAuctionSelectionRefresh = true;
        try {
            auctionTable.setItems(FXCollections.observableArrayList(filteredEntries));
            if (selectedId != null) {
                auctionTable.getItems().stream()
                        .filter(entry -> selectedId.equals(entry.getItemId()))
                        .findFirst()
                        .ifPresentOrElse(
                                entry -> auctionTable.getSelectionModel().select(entry),
                                () -> auctionTable.getSelectionModel().clearSelection()
                        );
            } else {
                auctionTable.getSelectionModel().clearSelection();
            }
        } finally {
            suppressAuctionSelectionRefresh = false;
        }
        AuctionListEntry selectedAuction = auctionTable.getSelectionModel().getSelectedItem();
        selectedAuctionId = selectedAuction == null ? null : selectedAuction.getItemId();
        updateAuctionSelectionActions();
        auctionTable.refresh();
        updateResultCountLabel(filteredEntries.size(), latestEntries.size());
    }

    private void updateResultCountLabel(int visibleCount, int totalCount) {
        if (totalCount == 0) {
            resultCountLabel.setText("No auctions are available right now.");
            return;
        }
        if (watchedOnlyCheckBox.isSelected() && visibleCount == 0) {
            resultCountLabel.setText("No watched auctions match the current filters.");
            return;
        }
        if (visibleCount == totalCount) {
            resultCountLabel.setText(totalCount + " auctions available");
            return;
        }
        resultCountLabel.setText("Showing " + visibleCount + " of " + totalCount + " auctions");
    }

    private void updateAuctionSelectionActions() {
        AuctionListEntry selectedAuction = auctionTable.getSelectionModel().getSelectedItem();
        boolean hasSelection = selectedAuction != null;
        openAuctionButton.setDisable(!hasSelection);
        toggleWatchButton.setDisable(!hasSelection);
        toggleWatchButton.setText(hasSelection && applicationSession.isAuctionWatched(selectedAuction.getItemId())
                ? "Unwatch"
                : "Watch");
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
