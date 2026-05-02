package org.example.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public final class SceneNavigator {
    private SceneNavigator() {
    }

    public static void switchScene(Node source, String resourcePath, String title) {
        Stage stage = (Stage) source.getScene().getWindow();
        switchScene(stage, resourcePath, title);
    }

    public static void switchScene(Stage stage, String resourcePath, String title) {
        Parent root = load(resourcePath);
        WindowLayoutProfile layoutProfile = WindowLayoutProfile.forResource(resourcePath);
        Scene scene = stage.getScene() == null
                ? new Scene(root, layoutProfile.preferredWidth(), layoutProfile.preferredHeight())
                : stage.getScene();

        if (scene.getRoot() != root) {
            scene.setRoot(root);
        }

        stage.setTitle(title);
        ResponsiveViewSupport.applyWindowProfile(stage, scene, resourcePath);
        stage.setScene(scene);
        stage.show();
    }

    private static Parent load(String resourcePath) {
        try {
            FXMLLoader loader = new FXMLLoader(SceneNavigator.class.getResource(resourcePath));
            if (loader.getLocation() == null) {
                throw new IllegalArgumentException("View not found: " + resourcePath);
            }
            return loader.load();
        } catch (IOException e) {
            throw new IllegalStateException("Could not open view: " + resourcePath, e);
        }
    }
}
