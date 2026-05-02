package org.example.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class AuctionDisplayFormatter {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private AuctionDisplayFormatter() {
    }

    public static String formatCurrency(double amount) {
        return String.format(Locale.US, "$%.2f", amount);
    }

    public static String formatDateTime(LocalDateTime value) {
        return value == null ? "" : DATE_TIME_FORMATTER.format(value);
    }

    public static String formatRemainingTime(long remainingSeconds) {
        if (remainingSeconds == Long.MAX_VALUE) {
            return "No deadline";
        }
        if (remainingSeconds <= 0L) {
            return "Ended";
        }

        long days = remainingSeconds / 86_400L;
        long hours = (remainingSeconds % 86_400L) / 3_600L;
        long minutes = (remainingSeconds % 3_600L) / 60L;
        long seconds = remainingSeconds % 60L;

        if (days > 0L) {
            return "%dd %02dh %02dm %02ds".formatted(days, hours, minutes, seconds);
        }
        if (hours > 0L) {
            return "%dh %02dm %02ds".formatted(hours, minutes, seconds);
        }
        return "%dm %02ds".formatted(minutes, seconds);
    }
}
