package org.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        // Lưu ý: Đường dẫn này phải cực chuẩn
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/main_view.fxml"));
        Parent root = loader.load();

        primaryStage.setTitle("Hệ thống Đấu giá - Tuần 7 Preview");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}