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

    public void saveItems(List<Item> items) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(FILE_PATH))) {
            oos.writeObject(items == null ? Collections.emptyList() : new ArrayList<>(items));
            System.out.println("Saved data to " + FILE_PATH);
        } catch (IOException e) {
            System.err.println("Failed to save file: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public List<Item> loadItems() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(FILE_PATH))) {
            return (List<Item>) ois.readObject();
        } catch (FileNotFoundException e) {
            System.out.println("No data file yet. Starting with an empty catalog.");
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Failed to read file: " + e.getMessage());
        }
        return Collections.emptyList();
    }
}
