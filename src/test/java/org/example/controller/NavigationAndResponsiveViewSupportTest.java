package org.example.controller;

import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.example.util.ResponsiveViewSupport;
import org.example.util.SceneNavigator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
