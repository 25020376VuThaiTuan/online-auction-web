package org.example.app;

import javafx.application.Application;
import javafx.stage.Stage;
import org.example.App;

public class MainApp extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        new App().start(stage);
    }
    public static void main(String[] args) {
        App.main(args);
    }
}
