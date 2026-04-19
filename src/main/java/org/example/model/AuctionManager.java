package org.example.model;
import java.util.ArrayList;
import java.util.List;

public class AuctionManager {
    private static AuctionManager instance;
    private List<Item> items;

    private AuctionManager() {
        items = new ArrayList<>();
    }

    public static AuctionManager getInstance() {
        if (instance == null) instance = new AuctionManager();
        return instance;
    }

    public void addItem(Item item) { items.add(item); }
    public List<Item> getItems() { return items; }
}