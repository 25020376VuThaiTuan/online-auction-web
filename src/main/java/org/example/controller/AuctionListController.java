package org.example.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Duration;
import org.example.service.AuctionWorkflowService;
import org.example.state.ApplicationSession;
import org.example.util.ResponsiveViewSupport;
import org.example.util.SceneNavigator;
import org.example.viewmodel.AuctionListEntry;

public class AuctionListController {
    private final AuctionWorkflowService workflowService = AuctionWorkflowService.getInstance();
    private final ApplicationSession applicationSession = ApplicationSession.getInstance();

    private Timeline refreshTimeline;

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
                setText(empty || item == null ? "" : item.replace('_', ' '));
            }
        });

        welcomeLabel.setText("Signed in as: " + applicationSession.getCurrentUserLabel());
        refreshTable();
        startRefreshLoop();
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
        applicationSession.logout();
        stopRefreshLoop();
        SceneNavigator.switchScene(auctionTable, "/view/Login.fxml", "Online Auction System");
    }

    private void refreshTable() {
        workflowService.refreshFromStoreIfChanged();
        String selectedId = auctionTable.getSelectionModel().getSelectedItem() == null
                ? null
                : auctionTable.getSelectionModel().getSelectedItem().getItemId();

        auctionTable.setItems(FXCollections.observableArrayList(workflowService.getAuctionListEntries()));
        if (selectedId != null) {
            for (AuctionListEntry entry : auctionTable.getItems()) {
                if (selectedId.equals(entry.getItemId())) {
                    auctionTable.getSelectionModel().select(entry);
                    break;
                }
            }
        }
        auctionTable.refresh();
    }

    private void startRefreshLoop() {
        // Refresh status/end-time driven data so the list stays accurate without reopening the screen.
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> refreshTable()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    private void stopRefreshLoop() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
