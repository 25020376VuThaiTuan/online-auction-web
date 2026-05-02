package org.example.util;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

public final class ResponsiveViewSupport {
    private ResponsiveViewSupport() {
    }

    public static void applyWindowProfile(Stage stage, Scene scene, String resourcePath) {
        WindowLayoutProfile profile = WindowLayoutProfile.forResource(resourcePath);

        stage.setResizable(true);
        stage.setMinWidth(profile.minimumWidth());
        stage.setMinHeight(profile.minimumHeight());

        if (stage.getWidth() < profile.minimumWidth()) {
            stage.setWidth(profile.preferredWidth());
        }
        if (stage.getHeight() < profile.minimumHeight()) {
            stage.setHeight(profile.preferredHeight());
        }

        if (stage.getScene() == null) {
            stage.setScene(scene);
        }
    }

    public static void configureResponsiveTable(TableView<?> table) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("No data available"));
    }

    public static <T> void configureCurrencyColumn(TableColumn<T, Double> column) {
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? "" : AuctionDisplayFormatter.formatCurrency(value));
            }
        });
    }
}
