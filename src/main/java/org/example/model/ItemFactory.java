package org.example.model;

import java.time.LocalDateTime;

public class ItemFactory {
    public static Item createItem(String type, String id, String itemName, String description,
                                  double startingPrice, LocalDateTime startTime, LocalDateTime endTime,
                                  String extraStr, int extraInt) {

        // Mới tạo thì giá hiện tại (currentPrice) bằng giá khởi điểm (startingPrice)

        return switch (type.toLowerCase()) {
            case "electronics" -> new Electronics(id, itemName, description, startingPrice, startingPrice,
                    startTime, endTime, extraStr, extraInt);
            case "art" -> new Art(id, itemName, description, startingPrice, startingPrice,
                    startTime, endTime, extraStr, extraInt);
            case "vehicle" -> new Vehicle(id, itemName, description, startingPrice, startingPrice,
                    startTime, endTime, extraStr, extraInt);
            default -> throw new IllegalArgumentException("Loại sản phẩm " + type + " không tồn tại!");
        };
    }
}