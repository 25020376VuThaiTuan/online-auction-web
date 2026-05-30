package org.example.exception;

import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GlobalExceptionHandler implements Thread.UncaughtExceptionHandler {
    private static final Logger LOGGER = Logger.getLogger(GlobalExceptionHandler.class.getName());
    private static final String LOG_FILE = "system_errors.log";

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        handleException(t, e);
    }

    public static void handleException(Thread t, Throwable e) {
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println("=== ERROR LOG ===");
            pw.println("Time: " + LocalDateTime.now());
            pw.println("Thread: " + (t != null ? t.getName() : "Unknown"));
            pw.println("Type: " + e.getClass().getName());
            pw.println("Details: " + e.getMessage());
            e.printStackTrace(pw);
            pw.println("=================");
            pw.println();
        } catch (IOException ioException) {
            LOGGER.log(Level.WARNING, "Failed to write exception log: {0}", ioException.getMessage());
        }

        if (Platform.isFxApplicationThread()) {
            showSafeAlert(e);
        } else {
            Platform.runLater(() -> showSafeAlert(e));
        }
    }

    private static void showSafeAlert(Throwable e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("System Notice");

        if (e instanceof InvalidBidException || e instanceof InsufficientBalanceException
                || e instanceof UserNotFound || e instanceof InvalidPasswordException) {
            alert.setHeaderText("Invalid Request");
            alert.setContentText(e.getMessage());
        } else {
            alert.setHeaderText("System Error");
            alert.setContentText("An unexpected error occurred. Please try again.");
        }

        alert.showAndWait();
    }
}
