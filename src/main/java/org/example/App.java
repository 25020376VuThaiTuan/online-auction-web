package org.example;

import javafx.application.Application;
import javafx.stage.Stage;
import org.example.util.SceneNavigator;

public class App extends Application {
    @Override
    public void start(Stage primaryStage) {
        SceneNavigator.switchScene(primaryStage, "/view/Login.fxml", "Online Auction System");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
