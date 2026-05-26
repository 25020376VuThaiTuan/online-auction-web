package org.example.controller;

import javafx.collections.FXCollections;
import javafx.scene.chart.LineChart;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import org.example.model.Bid;
import org.example.util.AuctionDisplayFormatter;
import org.example.util.BidChartUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

final class DashboardBidPanelPresenter {
    private static final int MAX_BID_ACTIVITY_LINES = 10;

    private final Label currentWinnerLabel;
    private final ListView<String> bidNotificationList;
    private final LineChart<String, Number> bidHistoryChart;

    private String lastBidHistoryChartSignature;

    DashboardBidPanelPresenter(
            Label currentWinnerLabel,
            ListView<String> bidNotificationList,
            LineChart<String, Number> bidHistoryChart
    ) {
        this.currentWinnerLabel = currentWinnerLabel;
        this.bidNotificationList = bidNotificationList;
        this.bidHistoryChart = bidHistoryChart;
    }

    void applyBidHistory(List<Bid> bidHistory, Function<String, String> bidderNameResolver) {
        List<Bid> safeHistory = bidHistory == null ? List.of() : bidHistory;
        applyCurrentWinner(safeHistory, bidderNameResolver);
        applyBidNotifications(safeHistory, bidderNameResolver);

        String signature = BidChartUtils.signature(safeHistory);
        if (signature.equals(lastBidHistoryChartSignature)) {
            return;
        }

        if (bidHistoryChart != null) {
            BidChartUtils.applyBidHistory(bidHistoryChart, safeHistory);
        }
        lastBidHistoryChartSignature = signature;
    }

    void addBidActivityNotification(String bidderName, double amount, LocalDateTime bidTime, String status) {
        String line = DashboardFormatters.formatBidNotificationTime(bidTime)
                + " - "
                + bidderName
                + " - "
                + AuctionDisplayFormatter.formatCurrency(amount)
                + " ("
                + status
                + ")";
        prependLine(line);
        if (currentWinnerLabel != null) {
            currentWinnerLabel.setText("Current winner: "
                    + bidderName
                    + " - "
                    + AuctionDisplayFormatter.formatCurrency(amount)
                    + " at "
                    + DashboardFormatters.formatBidNotificationTime(bidTime));
        }
    }

    void addMessage(String userName, String message) {
        prependLine(DashboardFormatters.formatBidNotificationTime(LocalDateTime.now())
                + " - "
                + userName
                + " - "
                + message);
    }

    void clearStatusViews() {
        if (currentWinnerLabel != null) {
            currentWinnerLabel.setText("Current winner: N/A");
        }
        if (bidNotificationList != null) {
            bidNotificationList.setItems(FXCollections.observableArrayList());
        }
    }

    void clearChart() {
        if (bidHistoryChart != null) {
            bidHistoryChart.getData().clear();
        }
        lastBidHistoryChartSignature = null;
    }

    private void applyCurrentWinner(List<Bid> bidHistory, Function<String, String> bidderNameResolver) {
        if (currentWinnerLabel == null) {
            return;
        }
        if (bidHistory.isEmpty()) {
            currentWinnerLabel.setText("Current winner: No bids yet");
            return;
        }

        Bid winningBid = bidHistory.get(bidHistory.size() - 1);
        currentWinnerLabel.setText("Current winner: "
                + bidderNameResolver.apply(winningBid.getBidderId())
                + " - "
                + AuctionDisplayFormatter.formatCurrency(winningBid.getAmount())
                + " at "
                + DashboardFormatters.formatBidNotificationTime(winningBid.getBidTime()));
    }

    private void applyBidNotifications(List<Bid> bidHistory, Function<String, String> bidderNameResolver) {
        if (bidNotificationList == null) {
            return;
        }
        if (bidHistory.isEmpty()) {
            bidNotificationList.setItems(FXCollections.observableArrayList());
            return;
        }

        List<String> lines = new ArrayList<>();
        for (int index = bidHistory.size() - 1; index >= 0 && lines.size() < MAX_BID_ACTIVITY_LINES; index--) {
            lines.add(formatBidNotificationLine(bidHistory.get(index), bidderNameResolver));
        }
        bidNotificationList.setItems(FXCollections.observableArrayList(lines));
    }

    private String formatBidNotificationLine(Bid bid, Function<String, String> bidderNameResolver) {
        return DashboardFormatters.formatBidNotificationTime(bid.getBidTime())
                + " - "
                + bidderNameResolver.apply(bid.getBidderId())
                + " - "
                + AuctionDisplayFormatter.formatCurrency(bid.getAmount());
    }

    private void prependLine(String line) {
        if (bidNotificationList == null) {
            return;
        }
        List<String> lines = new ArrayList<>(bidNotificationList.getItems());
        lines.add(0, line);
        if (lines.size() > MAX_BID_ACTIVITY_LINES) {
            lines = new ArrayList<>(lines.subList(0, MAX_BID_ACTIVITY_LINES));
        }
        bidNotificationList.setItems(FXCollections.observableArrayList(lines));
    }
}
