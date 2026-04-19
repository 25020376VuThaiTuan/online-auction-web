package org.example.model;

import java.io.*;
import java.util.List;

public class DataManager {
    private static DataManager instance;
    private final String FILE_PATH = "data.dat";

    private DataManager() {}

    public static DataManager getInstance() {
        if (instance == null) {
            instance = new DataManager();
        }
        return instance;
    }

    // Task: Lưu danh sách sản phẩm xuống file
    public void saveItems(List<Item> items) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(FILE_PATH))) {
            oos.writeObject(items);
            System.out.println("Đã lưu dữ liệu vào " + FILE_PATH);
        } catch (IOException e) {
            System.err.println("Lỗi khi lưu file: " + e.getMessage());
        }
    }

    // Task: Đọc danh sách sản phẩm từ file lên
    @SuppressWarnings("unchecked")
    public List<Item> loadItems() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(FILE_PATH))) {
            return (List<Item>) ois.readObject();
        } catch (FileNotFoundException e) {
            System.out.println("Chưa có file dữ liệu, tạo danh sách mới.");
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Lỗi khi đọc file: " + e.getMessage());
        }
        return null;
    }
}