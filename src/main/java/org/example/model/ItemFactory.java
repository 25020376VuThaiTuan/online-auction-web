package org.example.model;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemFactory {
    private static final Map<String, ItemCreator> ITEM_CREATORS = new ConcurrentHashMap<>();

    static {
        registerBuiltInType("electronics", request -> new Electronics(
                request.id(),
                request.itemName(),
                request.description(),
                request.startingPrice(),
                request.currentPrice(),
                request.startTime(),
                request.endTime(),
                request.extraText(),
                request.extraNumber()
        ));
        registerBuiltInType("art", request -> new Art(
                request.id(),
                request.itemName(),
                request.description(),
                request.startingPrice(),
                request.currentPrice(),
                request.startTime(),
                request.endTime(),
                request.extraText(),
                request.extraNumber()
        ));
        registerBuiltInType("vehicle", request -> new Vehicle(
                request.id(),
                request.itemName(),
                request.description(),
                request.startingPrice(),
                request.currentPrice(),
                request.startTime(),
                request.endTime(),
                request.extraText(),
                request.extraNumber()
        ));
    }

    private ItemFactory() {
    }

    public static Item createItem(String type, String id, String itemName, String description,
                                  double startingPrice, LocalDateTime startTime, LocalDateTime endTime,
                                  String extraStr, int extraInt) {
        ItemCreationRequest request = new ItemCreationRequest(
                id,
                itemName,
                description,
                startingPrice,
                startingPrice,
                startTime,
                endTime,
                extraStr,
                extraInt
        );
        return createItem(type, request);
    }

    public static Item createItem(String type, ItemCreationRequest request) {
        ItemCreator creator = ITEM_CREATORS.get(normalizeType(type));
        if (creator == null) {
            throw new IllegalArgumentException("Unsupported item type: " + type);
        }
        return creator.create(Objects.requireNonNull(request, "request"));
    }

    public static void registerType(String type, ItemCreator creator) {
        String normalizedType = normalizeType(type);
        ItemCreator previous = ITEM_CREATORS.putIfAbsent(normalizedType, Objects.requireNonNull(creator, "creator"));
        if (previous != null) {
            throw new IllegalArgumentException("Item type is already registered: " + normalizedType);
        }
    }

    public static Set<String> supportedTypes() {
        return Set.copyOf(ITEM_CREATORS.keySet());
    }

    private static void registerBuiltInType(String type, ItemCreator creator) {
        ITEM_CREATORS.put(normalizeType(type), creator);
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Item type is required.");
        }
        return type.trim().toLowerCase(Locale.ROOT);
    }

    @FunctionalInterface
    public interface ItemCreator {
        Item create(ItemCreationRequest request);
    }

    public record ItemCreationRequest(
            String id,
            String itemName,
            String description,
            double startingPrice,
            double currentPrice,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String extraText,
            int extraNumber
    ) {
    }
}
