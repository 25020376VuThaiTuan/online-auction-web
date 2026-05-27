package org.example.controller;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.example.util.ResponsiveViewSupport;
import org.example.util.SceneNavigator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NavigationAndResponsiveViewSupportTest {
    @BeforeAll
    static void initToolkit() {
        JavaFxTestSupport.startToolkit();
    }

    @Test
    void windowProfileSetsMinimumsAndKeepsExistingScene() {
        JavaFxTestSupport.runAndWait(() -> {
            Stage stage = new Stage();
            Scene scene = new Scene(new Pane(), 10.0, 10.0);
            stage.setWidth(100.0);
            stage.setHeight(100.0);

            ResponsiveViewSupport.applyWindowProfile(stage, scene, "/view/Dashboard.fxml");

            assertSame(scene, stage.getScene());
            assertEquals(980.0, stage.getMinWidth());
            assertEquals(720.0, stage.getMinHeight());
            assertEquals(980.0, stage.getWidth());
            assertEquals(720.0, stage.getHeight());

            ResponsiveViewSupport.applyWindowProfile(stage, new Scene(new Pane()), "/view/Login.fxml");

            assertSame(scene, stage.getScene());
            assertEquals(360.0, stage.getMinWidth());
            assertEquals(420.0, stage.getMinHeight());
            stage.close();
        });
    }

    @Test
    void navigatorReportsMissingViewsBeforeChangingStage() {
        JavaFxTestSupport.runAndWait(() -> {
            Stage stage = new Stage();

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> SceneNavigator.switchScene(stage, "/view/Missing.fxml", "Missing")
            );

            assertEquals("View not found: /view/Missing.fxml", exception.getMessage());
            assertNull(stage.getTitle());
            stage.close();
        });
    }

    @Test
    void navigatorLoadsFxmlIntoNewSceneAndThenReusesExistingSceneFromNode() {
        JavaFxTestSupport.runAndWait(() -> {
            Stage stage = new Stage();

            SceneNavigator.switchScene(stage, "/view/Test.fxml", "Test View");
            Scene firstScene = stage.getScene();

            assertEquals("Test View", stage.getTitle());
            assertInstanceOf(Pane.class, firstScene.getRoot());
            assertEquals(840.0, stage.getMinWidth());
            assertEquals(600.0, stage.getMinHeight());

            SceneNavigator.switchScene(firstScene.getRoot(), "/view/Test.fxml", "Second View");

            assertSame(firstScene, stage.getScene());
            assertEquals("Second View", stage.getTitle());
            assertInstanceOf(Pane.class, stage.getScene().getRoot());
            stage.close();
        });
    }

    @Test
    void responsiveTableHelpersConfigurePlaceholderAndCurrencyCells() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            try {
                TableView<Object> table = new TableView<>();
                ResponsiveViewSupport.configureResponsiveTable(table);

                assertInstanceOf(Label.class, table.getPlaceholder());
                assertEquals("No data available", ((Label) table.getPlaceholder()).getText());

                TableColumn<Object, Double> column = new TableColumn<>("Amount");
                ResponsiveViewSupport.configureCurrencyColumn(column);
                TableCell<Object, Double> cell = column.getCellFactory().call(column);
                Method updateItem = findUpdateItem(cell);

                updateItem.invoke(cell, 12.5, false);
                assertEquals("$12.50", cell.getText());
                updateItem.invoke(cell, null, false);
                assertEquals("", cell.getText());
                updateItem.invoke(cell, 99.0, true);
                assertEquals("", cell.getText());
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    private static Method findUpdateItem(TableCell<Object, Double> cell) throws NoSuchMethodException {
        Class<?> type = cell.getClass();
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if ("updateItem".equals(method.getName()) && method.getParameterCount() == 2) {
                    method.setAccessible(true);
                    return method;
                }
            }
            type = type.getSuperclass();
        }
        throw new NoSuchMethodException("updateItem");
    }
}
