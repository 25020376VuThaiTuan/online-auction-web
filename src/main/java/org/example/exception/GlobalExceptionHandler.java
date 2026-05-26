package org.example.exception;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;

public class GlobalExceptionHandler implements Thread.UncaughtExceptionHandler {
    private static final String LOG_FILE = "system_errors.log";

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        handleException(t, e);
    }

    public static void handleException(Thread t, Throwable e) {
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println("=== ERROR LOG ===");
            pw.println("Thời gian: " + LocalDateTime.now());
            pw.println("Luồng: " + (t != null ? t.getName() : "Unknown"));
            pw.println("Loại lỗi: " + e.getClass().getName());
            pw.println("Chi tiết: " + e.getMessage());
            e.printStackTrace(pw);
            pw.println("=================\n");
        } catch (IOException ioException) {
            System.err.println("Lỗi: " + ioException.getMessage());
        }

        if (Platform.isFxApplicationThread()) {
            showSafeAlert(e);
        } else {
            Platform.runLater(() -> showSafeAlert(e));
        }
    }

    private static void showSafeAlert(Throwable e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Thông báo hệ thống");

        if (e instanceof InvalidBidException || e instanceof InsufficientBalanceException
                || e instanceof UserNotFound || e instanceof InvalidPasswordException) {
            alert.setHeaderText("Yêu cầu không hợp lệ");
            alert.setContentText(e.getMessage());
        } else {
            alert.setHeaderText("Sự cố hệ thống");
            alert.setContentText("Đã xảy ra lỗi không xác định. Vui lòng thử lại sau.");
        }

        alert.showAndWait();
    }
}