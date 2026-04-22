package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.ListView;

public class AuctionListController {

    @FXML
    private ListView<String> auctionList;

    @FXML
    public void initialize() {

        auctionList.getItems().add("Laptop Auction");
        auctionList.getItems().add("Car Auction");
        auctionList.getItems().add("Painting Auction");

    }
}