package org.example.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.example.model.DataManager;
import org.example.model.Item;
import org.example.exception.InvalidBidException;

import java.util.List;

public class AuctionController {
    @FXML private TableView<Item> itemTable;
    @FXML private TableColumn<Item, String> nameColumn;
    @FXML private TableColumn<Item, Double> priceColumn;
    @FXML private TableColumn<Item, String> timeColumn;
    @FXML private TextField bidAmountField;

    @FXML
    public void initialize() {
        // 1. Định nghĩa xem mỗi cột lấy dữ liệu từ thuộc tính nào của lớp Item
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("currentPrice"));
        timeColumn.setCellValueFactory(new PropertyValueFactory<>("endTimeString"));

        // 2. Load dữ liệu từ file .dat (Tuần 8)
        List<Item> savedItems = DataManager.getInstance().loadItems();

        if (savedItems != null) {
            ObservableList<Item> data = FXCollections.observableArrayList(savedItems);
            itemTable.setItems(data);
        }
    }

    @FXML
    public void handlePlaceBid() {
        Item selectedItem = itemTable.getSelectionModel().getSelectedItem();

        if (selectedItem == null) {
            showAlert("Lỗi", "Vui lòng chọn một món đồ để đặt giá!");
            return;
        }

        try {
            double bidAmount = Double.parseDouble(bidAmountField.getText());

            // Logic bẫy lỗi tuần 8 của mày đây
            if (bidAmount <= selectedItem.getCurrentPrice()) {
                throw new InvalidBidException("Giá thầu phải cao hơn giá hiện tại!");
            }

            // Nếu ok thì cập nhật giá (tạm thời trên UI)
            selectedItem.setCurrentPrice(bidAmount);
            itemTable.refresh(); // Cập nhật lại bảng
            showAlert("Thành công", "Mày đã đặt giá thành công cho " + selectedItem.getItemName());

        } catch (NumberFormatException e) {
            showAlert("Lỗi nhập liệu", "Vui lòng nhập số tiền hợp lệ!");
        } catch (InvalidBidException e) {
            showAlert("Lỗi đấu giá", e.getMessage());
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }


}