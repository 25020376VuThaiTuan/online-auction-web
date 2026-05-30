package org.example.util;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tooltip;
import org.example.model.Bid;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public final class BidChartUtils {
    private static final DateTimeFormatter CHART_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String SERIES_NAME = "Bid history";
    private static final String LATEST_POINT_STYLE = "latest-bid-point";
    private static final String TOOLTIP_KEY = "auction.bidChartTooltip";

    private BidChartUtils() {
    }

    public static void configureLiveBidChart(LineChart<String, Number> chart) {
        if (chart == null) {
            return;
        }
        chart.setAnimated(true);
        chart.setCreateSymbols(true);
        chart.setLegendVisible(false);
        if (chart.getXAxis() != null) {
            chart.getXAxis().setTickLabelsVisible(false);
            chart.getXAxis().setTickMarkVisible(false);
            chart.getXAxis().setLabel("Bid sequence");
        }
        if (!chart.getStyleClass().contains("live-bid-chart")) {
            chart.getStyleClass().add("live-bid-chart");
        }
    }

    public static void applyBidHistory(LineChart<String, Number> chart, List<Bid> bidHistory) {
        applyBidHistory(chart, bidHistory, Function.identity());
    }

    public static void applyBidHistory(
            LineChart<String, Number> chart,
            List<Bid> bidHistory,
            Function<String, String> bidderNameResolver
    ) {
        if (chart == null) {
            return;
        }
        List<Bid> safeHistory = bidHistory == null ? List.of() : bidHistory;
        Function<String, String> safeBidderNameResolver = bidderNameResolver == null ? Function.identity() : bidderNameResolver;
        XYChart.Series<String, Number> series = getOrCreateSeries(chart);

        while (series.getData().size() > safeHistory.size()) {
            series.getData().remove(series.getData().size() - 1);
        }

        for (int index = 0; index < safeHistory.size(); index++) {
            Bid bid = safeHistory.get(index);
            String label = chartLabel(index, bid);
            String tooltip = tooltipText(index, bid, safeBidderNameResolver);
            if (index < series.getData().size()) {
                XYChart.Data<String, Number> data = series.getData().get(index);
                data.setXValue(label);
                data.setYValue(MoneyUtils.roundCurrency(bid.getAmount()));
                data.setExtraValue(tooltip);
            } else {
                series.getData().add(new XYChart.Data<>(label, MoneyUtils.roundCurrency(bid.getAmount()), tooltip));
            }
        }

        Platform.runLater(() -> styleDataNodes(series));
    }

    public static String signature(List<Bid> bidHistory) {
        if (bidHistory == null || bidHistory.isEmpty()) {
            return "";
        }
        StringBuilder signature = new StringBuilder();
        for (Bid bid : bidHistory) {
            signature.append(value(bid.getId()))
                    .append(':')
                    .append(MoneyUtils.roundCurrency(bid.getAmount()))
                    .append(':')
                    .append(bid.getBidTime() == null ? "" : bid.getBidTime())
                    .append('|');
        }
        return signature.toString();
    }

    private static XYChart.Series<String, Number> getOrCreateSeries(LineChart<String, Number> chart) {
        if (!chart.getData().isEmpty()) {
            return chart.getData().get(0);
        }
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(SERIES_NAME);
        chart.getData().add(series);
        return series;
    }

    private static String chartLabel(int index, Bid bid) {
        String timeLabel = bid.getBidTime() == null ? "N/A" : CHART_TIME_FORMATTER.format(bid.getBidTime());
        return String.format(Locale.US, "#%02d %s", index + 1, timeLabel);
    }

    private static String tooltipText(int index, Bid bid, Function<String, String> bidderNameResolver) {
        String bidder = value(bid.getBidderId()).isBlank() ? "Unknown bidder" : value(bidderNameResolver.apply(bid.getBidderId()));
        if (bidder.isBlank()) {
            bidder = "Unknown bidder";
        }
        String time = bid.getBidTime() == null ? "N/A" : bid.getBidTime().format(DateTimeFormatter.ofPattern("dd/MM HH:mm:ss"));
        return String.format(
                Locale.US,
                "Bid #%02d%n%s%n%s%n%s",
                index + 1,
                bidder,
                AuctionDisplayFormatter.formatCurrency(bid.getAmount()),
                time
        );
    }

    private static void styleDataNodes(XYChart.Series<String, Number> series) {
        int lastIndex = series.getData().size() - 1;
        for (int index = 0; index < series.getData().size(); index++) {
            XYChart.Data<String, Number> data = series.getData().get(index);
            Node node = data.getNode();
            if (node == null) {
                continue;
            }
            node.getStyleClass().remove(LATEST_POINT_STYLE);
            if (index == lastIndex) {
                node.getStyleClass().add(LATEST_POINT_STYLE);
            }
            installOrUpdateTooltip(node, String.valueOf(data.getExtraValue()));
        }
    }

    private static void installOrUpdateTooltip(Node node, String text) {
        Object existing = node.getProperties().get(TOOLTIP_KEY);
        if (existing instanceof Tooltip tooltip) {
            tooltip.setText(text);
            return;
        }
        Tooltip tooltip = new Tooltip(text);
        Tooltip.install(node, tooltip);
        node.getProperties().put(TOOLTIP_KEY, tooltip);
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
