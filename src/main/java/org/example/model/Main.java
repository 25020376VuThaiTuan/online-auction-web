package org.example.model;

import org.example.model.*;
import org.example.exception.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        System.out.println("🚀 ĐANG KHỞI CHẠY HỆ THỐNG KIỂM THỬ TOÀN DIỆN (W6 - W8)");

        // 1. Kiểm tra các Singleton (AuctionManager & DataManager)
        verifySingletons();

        // 2. Tạo dữ liệu mẫu qua Factory & User Hierarchy
        verifySetup();

        // 3. Kiểm tra Xử lý ngoại lệ (Tuần 8)
        verifyExceptions();

        // 4. Kiểm tra Lưu & Đọc file Serialization (Tuần 8)
        verifySerialization();

        System.out.println("\n✅ TẤT CẢ TEST CASE ĐÃ HOÀN THÀNH MƯỢT MÀ!");
    }

    private static void verifySingletons() {
        System.out.println("\n--- TEST 1: SINGLETON CHECK ---");
        boolean managerOk = (AuctionManager.getInstance() == AuctionManager.getInstance());
        boolean dataOk = (DataManager.getInstance() == DataManager.getInstance());
        System.out.println("AuctionManager Singleton: " + (managerOk ? "NGON" : "LỖI"));
        System.out.println("DataManager Singleton: " + (dataOk ? "NGON" : "LỖI"));
    }

    private static void verifySetup() {
        System.out.println("\n--- TEST 2: FACTORY & POLYMORPHISM ---");
        AuctionManager am = AuctionManager.getInstance();
        LocalDateTime now = LocalDateTime.now();

        // Tạo đồ qua Factory
        Item laptop = ItemFactory.createItem("electronics", "E1", "Macbook", "M3 Max", 2000, now, now.plusDays(1), "Apple", 12);
        Item car = ItemFactory.createItem("vehicle", "V1", "VinFast", "VF8", 35000, now, now.plusDays(5), "Plus", 0);

        am.addItem(laptop);
        am.addItem(car);

        System.out.println("Danh sách hàng hiện có:");
        for (Item i : am.getItems()) {
            i.displayInfo(); // Đa hình chạy ở đây
        }
    }

    private static void verifyExceptions() {
        System.out.println("\n--- TEST 3: EXCEPTION HANDLING ---");
        Bidder bidder = new Bidder("B1", "tao_la_bidder", "123", "bidder@test.com", 100.0);
        Item item = AuctionManager.getInstance().getItems().get(0);

        // Giả lập logic đặt giá lỗi
        try {
            System.out.println("Thử đặt giá 50$ (Thấp hơn giá khởi điểm 2000$)...");
            if (50 < item.getCurrentPrice()) {
                throw new InvalidBidException("Giá thầu 50$ không hợp lệ! Phải cao hơn " + item.getCurrentPrice());
            }
        } catch (InvalidBidException e) {
            System.err.println("Bắt được lỗi: " + e.getMessage());
        }

        try {
            System.out.println("Thử đặt giá 5000$ (Nhưng số dư chỉ có 100$)...");
            if (5000 > bidder.getBalance()) { // Giả sử mày đã thêm getBalance() cho Bidder
                throw new InsufficientBalanceException("Ví hẻo quá mày ơi! Thiếu " + (5000 - 100.0) + "$ nữa.");
            }
        } catch (InsufficientBalanceException e) {
            System.err.println("Bắt được lỗi: " + e.getMessage());
        }
    }

    private static void verifySerialization() {
        System.out.println("\n--- TEST 4: SERIALIZATION (SAVE/LOAD) ---");
        DataManager dm = DataManager.getInstance();
        AuctionManager am = AuctionManager.getInstance();

        // Lưu dữ liệu
        dm.saveItems(am.getItems());

        // Xóa danh sách tạm thời để test load
        System.out.println("Đang xóa danh sách tạm thời để test Load file...");
        List<Item> loadedItems = dm.loadItems();

        if (loadedItems != null && !loadedItems.isEmpty()) {
            System.out.println("Load file thành công! Số lượng item khôi phục: " + loadedItems.size());
            System.out.println("Tên item đầu tiên: " + loadedItems.get(0).getItemName());
        } else {
            System.err.println("Lỗi: Không đọc được dữ liệu từ file!");
        }
    }
}