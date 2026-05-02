package org.example.auction;

import org.example.model.Item;
import org.example.model.ItemFactory;

import java.time.LocalDateTime;
import java.util.List;

public final class AuctionSeedData {
    private AuctionSeedData() {
    }

    public static List<Item> createDemoItems() {
        LocalDateTime now = LocalDateTime.now();

        return List.of(
                ItemFactory.createItem(
                        "electronics",
                        "ELEC-001",
                        "MacBook Pro 16",
                        "Apple M3 Max, 36GB RAM, 1TB SSD",
                        2500.0,
                        now.minusHours(2),
                        now.plusHours(6),
                        "Apple",
                        24
                ),
                ItemFactory.createItem(
                        "art",
                        "ART-001",
                        "Modern Canvas Painting",
                        "Signed abstract work from a local artist",
                        850.0,
                        now.minusMinutes(30),
                        now.plusHours(2),
                        "Lan Nguyen",
                        2024
                ),
                ItemFactory.createItem(
                        "vehicle",
                        "VEH-001",
                        "Honda SH 150i",
                        "Single owner city scooter",
                        3200.0,
                        now.minusHours(1),
                        now.plusDays(1),
                        "2023",
                        6800
                )
        );
    }
}
