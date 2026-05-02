package org.example.model;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DataManager {
    private static DataManager instance;
    private static final String FILE_PATH = "data.dat";

    private DataManager() {
    }

    public static DataManager getInstance() {
        if (instance == null) {
            instance = new DataManager();
        }
        return instance;
    }

    public void saveStore(AuctionStore store) {
        AuctionStore safeStore = store == null ? AuctionStore.empty() : store;

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(FILE_PATH))) {
            oos.writeObject(safeStore);
            System.out.println("Saved data to " + FILE_PATH);
        } catch (IOException e) {
            System.err.println("Failed to save file: " + e.getMessage());
        }
    }

    public void saveItems(List<Item> items) {
        saveStore(new AuctionStore(items, Map.of()));
    }

    public AuctionStore loadStore() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(FILE_PATH))) {
            Object loadedObject = ois.readObject();

            if (loadedObject instanceof AuctionStore store) {
                return store;
            }

            // Keep old List<Item> files readable so existing data keeps working.
            if (loadedObject instanceof List<?> rawList) {
                List<Item> migratedItems = new ArrayList<>();
                for (Object candidate : rawList) {
                    if (candidate instanceof Item item) {
                        migratedItems.add(item);
                    }
                }
                return new AuctionStore(migratedItems, Map.of());
            }
        } catch (FileNotFoundException e) {
            System.out.println("No data file yet. Starting with an empty catalog.");
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Failed to read file: " + e.getMessage());
        }
        return AuctionStore.empty();
    }

    public List<Item> loadItems() {
        AuctionStore store = loadStore();
        return store.getItems().isEmpty() ? Collections.emptyList() : store.getItems();
    }
}
