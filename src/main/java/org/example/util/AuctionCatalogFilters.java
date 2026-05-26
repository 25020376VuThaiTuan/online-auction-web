package org.example.util;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

public final class AuctionCatalogFilters {
    public static final String ALL_STATUSES = "All statuses";
    public static final String SORT_ENDING_SOON = "Ending soonest";
    public static final String SORT_WATCHED_FIRST = "Watched first";
    public static final String SORT_PRICE_LOW = "Price low to high";
    public static final String SORT_PRICE_HIGH = "Price high to low";
    public static final String SORT_NAME = "Name A to Z";

    private AuctionCatalogFilters() {
    }

    public static List<String> statusOptions() {
        return List.of(
                ALL_STATUSES,
                "OPEN",
                "RUNNING",
                "FINISHED",
                "PAID",
                "CANCELLED"
        );
    }

    public static List<String> sortOptions() {
        return List.of(
                SORT_ENDING_SOON,
                SORT_WATCHED_FIRST,
                SORT_PRICE_LOW,
                SORT_PRICE_HIGH,
                SORT_NAME
        );
    }

    public static <T> List<T> filterAndSort(
            Collection<T> entries,
            FilterRequest request,
            EntryAdapter<T> adapter,
            Predicate<String> watchedAuction
    ) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        FilterRequest safeRequest = request == null ? FilterRequest.empty() : request;
        Predicate<String> safeWatchedAuction = watchedAuction == null ? ignored -> false : watchedAuction;
        String searchText = normalizeText(safeRequest.searchText());
        String selectedStatus = safeRequest.selectedStatus();

        return entries.stream()
                .filter(entry -> matchesSearch(adapter.itemName(entry), searchText))
                .filter(entry -> matchesStatus(adapter.status(entry), selectedStatus))
                .filter(entry -> !safeRequest.openOnly() || isOpenStatus(adapter.status(entry)))
                .filter(entry -> !safeRequest.watchedOnly() || safeWatchedAuction.test(adapter.itemId(entry)))
                .sorted((left, right) -> compareEntries(left, right, safeRequest.selectedSort(), adapter, safeWatchedAuction))
                .toList();
    }

    public static boolean isOpenStatus(String status) {
        String normalizedStatus = normalizeStatus(status);
        return !"FINISHED".equals(normalizedStatus)
                && !"PAID".equals(normalizedStatus)
                && !"CANCELLED".equals(normalizedStatus);
    }

    public static String normalizeStatus(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public static String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static String statusStyleClass(String status) {
        return switch (normalizeStatus(status)) {
            case "RUNNING" -> "status-running";
            case "FINISHED" -> "status-finished";
            case "PAID" -> "status-paid";
            case "CANCELLED" -> "status-cancelled";
            default -> "status-open";
        };
    }

    private static boolean matchesSearch(String itemName, String searchText) {
        return searchText.isBlank() || normalizeText(itemName).contains(searchText);
    }

    private static boolean matchesStatus(String status, String selectedStatus) {
        return selectedStatus == null
                || ALL_STATUSES.equals(selectedStatus)
                || normalizeStatus(status).equals(selectedStatus);
    }

    private static <T> int compareEntries(
            T left,
            T right,
            String selectedSort,
            EntryAdapter<T> adapter,
            Predicate<String> watchedAuction
    ) {
        if (SORT_PRICE_LOW.equals(selectedSort)) {
            return Double.compare(adapter.currentPrice(left), adapter.currentPrice(right));
        }
        if (SORT_PRICE_HIGH.equals(selectedSort)) {
            return Double.compare(adapter.currentPrice(right), adapter.currentPrice(left));
        }
        if (SORT_NAME.equals(selectedSort)) {
            return normalizeText(adapter.itemName(left)).compareTo(normalizeText(adapter.itemName(right)));
        }
        if (SORT_WATCHED_FIRST.equals(selectedSort)) {
            int watchedComparison = Boolean.compare(
                    watchedAuction.test(adapter.itemId(right)),
                    watchedAuction.test(adapter.itemId(left))
            );
            if (watchedComparison != 0) {
                return watchedComparison;
            }
        }
        return Long.compare(adapter.remainingSeconds(left), adapter.remainingSeconds(right));
    }

    public record FilterRequest(
            String searchText,
            String selectedStatus,
            String selectedSort,
            boolean openOnly,
            boolean watchedOnly
    ) {
        public static FilterRequest empty() {
            return new FilterRequest("", ALL_STATUSES, SORT_ENDING_SOON, false, false);
        }
    }

    public interface EntryAdapter<T> {
        String itemId(T entry);

        String itemName(T entry);

        String status(T entry);

        double currentPrice(T entry);

        long remainingSeconds(T entry);
    }
}
