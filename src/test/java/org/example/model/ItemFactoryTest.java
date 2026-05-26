package org.example.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemFactoryTest {
    @Test
    void builtInFactoryKeepsStartingPriceAsInitialCurrentPrice() {
        Item item = ItemFactory.createItem(
                " Electronics ",
                "ITEM-FACTORY-1",
                "Camera",
                "Mirrorless camera",
                250.0,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(1),
                "Brand",
                12
        );

        assertInstanceOf(Electronics.class, item);
        assertEquals(250.0, item.getCurrentPrice(), 0.001);
    }

    @Test
    void customTypesCanBeRegisteredWithoutChangingFactoryCode() {
        String type = "collectible-" + System.nanoTime();
        ItemFactory.registerType(type, request -> new TestItem(
                request.id(),
                request.itemName(),
                request.description(),
                request.startingPrice(),
                request.currentPrice(),
                request.startTime(),
                request.endTime()
        ));

        Item item = ItemFactory.createItem(
                type,
                "ITEM-CUSTOM-1",
                "Signed poster",
                "Limited edition",
                100.0,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(1),
                "",
                0
        );

        assertInstanceOf(TestItem.class, item);
        assertTrue(ItemFactory.supportedTypes().contains(type));
    }

    @Test
    void duplicateTypeRegistrationIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ItemFactory.registerType("electronics", request -> new TestItem(
                        request.id(),
                        request.itemName(),
                        request.description(),
                        request.startingPrice(),
                        request.currentPrice(),
                        request.startTime(),
                        request.endTime()
                ))
        );
    }

    private static final class TestItem extends Item {
        private TestItem(
                String id,
                String itemName,
                String description,
                double startingPrice,
                double currentPrice,
                LocalDateTime startTime,
                LocalDateTime endTime
        ) {
            super(id, itemName, description, startingPrice, currentPrice, startTime, endTime);
        }

        @Override
        public void displayInfo() {
        }
    }
}
