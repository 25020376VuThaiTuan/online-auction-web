package org.example.controller;

import org.example.model.Bid;
import org.example.model.WalletTransaction;
import org.example.util.AuctionCatalogFilters;
import org.example.util.AuctionDisplayFormatter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class DashboardFormatters {
    private static final DateTimeFormatter BID_NOTIFICATION_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");
    private static final DateTimeFormatter DASHBOARD_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private DashboardFormatters() {
    }

    static String formatBidNotificationTime(LocalDateTime value) {
        return value == null ? "N/A" : BID_NOTIFICATION_TIME_FORMATTER.format(value);
    }

    static String formatDateTime(LocalDateTime value) {
        return value == null ? "N/A" : DASHBOARD_DATE_TIME_FORMATTER.format(value);
    }

    static String formatSellerBidHistoryLine(Bid bid) {
        return bid.getBidderId()
                + " -> "
                + AuctionDisplayFormatter.formatCurrency(bid.getAmount())
                + " at "
                + formatBidNotificationTime(bid.getBidTime());
    }

    static String walletAuditLine(WalletTransaction transaction) {
        return formatDateTime(transaction.createdAt())
                + " - "
                + transaction.userId()
                + " - "
                + transaction.transactionType().replace('_', ' ')
                + " - "
                + AuctionDisplayFormatter.formatCurrency(transaction.amount())
                + " - balance "
                + AuctionDisplayFormatter.formatCurrency(transaction.balanceAfter())
                + (transaction.note() == null || transaction.note().isBlank() ? "" : " - " + transaction.note());
    }

    static String eligibleStyleClass(String value) {
        String normalizedValue = AuctionCatalogFilters.normalizeText(value);
        if ("entered".equals(normalizedValue)) {
            return "eligible-entered";
        }
        if ("can enter".equals(normalizedValue)) {
            return "eligible-ready";
        }
        return "eligible-blocked";
    }

    static String value(String text) {
        return text == null ? "" : text.trim();
    }
}
