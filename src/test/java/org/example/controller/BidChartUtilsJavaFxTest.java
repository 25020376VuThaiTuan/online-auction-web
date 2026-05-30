package org.example.controller;

import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import org.example.model.Bid;
import org.example.util.BidChartUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BidChartUtilsJavaFxTest {
    @BeforeAll
    static void initToolkit() {
        JavaFxTestSupport.startToolkit();
    }

    @Test
    void liveBidChartConfigurationAndHistoryUpdatesMutateChartState() {
        JavaFxTestSupport.runAndWait(() -> {
            LineChart<String, Number> chart = new LineChart<>(new CategoryAxis(), new NumberAxis());

            BidChartUtils.configureLiveBidChart(null);
            BidChartUtils.applyBidHistory(null, List.of());
            BidChartUtils.configureLiveBidChart(chart);
            BidChartUtils.configureLiveBidChart(chart);

            assertTrue(chart.getCreateSymbols());
            assertFalse(chart.isLegendVisible());
            assertFalse(chart.getXAxis().isTickLabelsVisible());
            assertFalse(chart.getXAxis().isTickMarkVisible());
            assertEquals("Bid sequence", chart.getXAxis().getLabel());
            assertEquals(1, chart.getStyleClass().stream().filter("live-bid-chart"::equals).count());

            LocalDateTime firstTime = LocalDateTime.of(2026, 5, 27, 10, 15, 30);
            LocalDateTime secondTime = LocalDateTime.of(2026, 5, 27, 10, 20, 45);
            BidChartUtils.applyBidHistory(chart, List.of(
                    new Bid("BID-1", "BIDDER-1", "ITEM-1", 100.004, firstTime),
                    new Bid("BID-2", " ", "ITEM-1", 125.005, secondTime)
            ));

            XYChart.Series<String, Number> series = chart.getData().getFirst();
            assertEquals("Bid history", series.getName());
            assertEquals(2, series.getData().size());
            assertEquals("#01 10:15:30", series.getData().get(0).getXValue());
            assertEquals(100.0, series.getData().get(0).getYValue().doubleValue(), 0.001);
            assertEquals("Bid #02\r\nUnknown bidder\r\n$125.01\r\n27/05 10:20:45".replace("\r\n", System.lineSeparator()),
                    String.valueOf(series.getData().get(1).getExtraValue()));

            BidChartUtils.applyBidHistory(chart, List.of(
                    new Bid("BID-1", "BIDDER-1", "ITEM-1", 100.004, firstTime)
            ), bidderId -> "BIDDER-1".equals(bidderId) ? "Alice Bidder" : bidderId);
            assertTrue(String.valueOf(series.getData().getFirst().getExtraValue()).contains("Alice Bidder"));

            BidChartUtils.applyBidHistory(chart, List.of(new Bid("BID-3", "BIDDER-3", "ITEM-1", 140.0, null)));

            assertEquals(1, series.getData().size());
            assertSame(series, chart.getData().getFirst());
            assertEquals("#01 N/A", series.getData().getFirst().getXValue());
            assertEquals(140.0, series.getData().getFirst().getYValue().doubleValue(), 0.001);

            BidChartUtils.applyBidHistory(chart, null);

            assertEquals(0, series.getData().size());
        });
    }

    @Test
    void chartNodeStylingMarksLatestPointAndUpdatesTooltip() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            try {
                XYChart.Series<String, Number> series = new XYChart.Series<>();
                XYChart.Data<String, Number> first = new XYChart.Data<>("#01", 100.0, "first");
                XYChart.Data<String, Number> second = new XYChart.Data<>("#02", 125.0, "second");
                Node firstNode = new StackPane();
                Node secondNode = new StackPane();
                first.setNode(firstNode);
                second.setNode(secondNode);
                series.getData().add(first);
                series.getData().add(second);

                Method styleDataNodes = BidChartUtils.class.getDeclaredMethod("styleDataNodes", XYChart.Series.class);
                styleDataNodes.setAccessible(true);
                styleDataNodes.invoke(null, series);

                assertFalse(firstNode.getStyleClass().contains("latest-bid-point"));
                assertTrue(secondNode.getStyleClass().contains("latest-bid-point"));
                assertTooltipText(firstNode, "first");
                assertTooltipText(secondNode, "second");

                second.setExtraValue("updated");
                styleDataNodes.invoke(null, series);

                assertEquals(1, secondNode.getStyleClass().stream().filter("latest-bid-point"::equals).count());
                assertTooltipText(secondNode, "updated");
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
    }

    private static void assertTooltipText(Node node, String expected) {
        Object tooltip = node.getProperties().get("auction.bidChartTooltip");
        assertTrue(tooltip instanceof Tooltip);
        assertEquals(expected, ((Tooltip) tooltip).getText());
    }
}
