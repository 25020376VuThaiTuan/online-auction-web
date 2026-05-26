package org.example.auction;

import org.example.model.Item;
import org.example.model.ItemFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuctionSessionRegistryTest {

    private AuctionSessionRegistry registry;

    @BeforeEach
    void setup() {

        registry =
                AuctionSessionRegistry.getInstance();

        registry.clear();
    }

    @Test
    void shouldCreateSession() {

        Item item =
                ItemFactory.createItem(
                        "art",
                        "item-1",
                        "Mona Lisa",
                        "Painting",
                        1000,
                        LocalDateTime.now(),
                        LocalDateTime.now().plusDays(1),
                        "Da Vinci",
                        1503
                );

        AuctionSession session =
                registry.getOrCreateSession(item);

        assertNotNull(session);
    }

    @Test
    void shouldFindSessionByItemId() {

        Item item =
                ItemFactory.createItem(
                        "electronics",
                        "item-2",
                        "Laptop",
                        "Gaming Laptop",
                        2000,
                        LocalDateTime.now(),
                        LocalDateTime.now().plusDays(1),
                        "Dell",
                        24
                );

        registry.getOrCreateSession(item);

        AuctionSession result =
                registry.findByItemId("item-2");

        assertNotNull(result);
    }

    @Test
    void shouldReturnSameSession() {

        Item item =
                ItemFactory.createItem(
                        "vehicle",
                        "item-3",
                        "BMW",
                        "Luxury Car",
                        5000,
                        LocalDateTime.now(),
                        LocalDateTime.now().plusDays(1),
                        "X5",
                        10000
                );

        AuctionSession session1 =
                registry.getOrCreateSession(item);

        AuctionSession session2 =
                registry.getOrCreateSession(item);

        assertSame(session1, session2);
    }

    @Test
    void shouldPreloadSessions() {

        Item item1 =
                ItemFactory.createItem(
                        "art",
                        "item-4",
                        "Painting",
                        "Art Item",
                        1000,
                        LocalDateTime.now(),
                        LocalDateTime.now().plusDays(1),
                        "Artist",
                        2020
                );

        Item item2 =
                ItemFactory.createItem(
                        "electronics",
                        "item-5",
                        "Phone",
                        "Smartphone",
                        500,
                        LocalDateTime.now(),
                        LocalDateTime.now().plusDays(1),
                        "Samsung",
                        12
                );

        registry.preloadSessions(
                List.of(item1, item2)
        );

        assertNotNull(
                registry.findByItemId("item-4")
        );

        assertNotNull(
                registry.findByItemId("item-5")
        );
    }

    @Test
    void shouldClearSessions() {

        Item item =
                ItemFactory.createItem(
                        "art",
                        "item-6",
                        "Artwork",
                        "Desc",
                        1000,
                        LocalDateTime.now(),
                        LocalDateTime.now().plusDays(1),
                        "Artist",
                        2021
                );

        registry.getOrCreateSession(item);

        registry.clear();

        AuctionSession result =
                registry.findByItemId("item-6");

        assertNull(result);
    }
}