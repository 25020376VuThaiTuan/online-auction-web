package org.example.exception;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class GlobalExceptionHandlerCoverageTest {
    private static final Path LOG_FILE = Path.of("system_errors.log");

    private String originalLogContent;
    private boolean logExisted;

    @BeforeAll
    static void initToolkit() {
        startToolkit();
    }

    @BeforeEach
    void preserveLog() throws Exception {
        logExisted = Files.exists(LOG_FILE);
        originalLogContent = logExisted ? Files.readString(LOG_FILE) : null;
        Files.deleteIfExists(LOG_FILE);
    }

    @AfterEach
    void restoreLog() throws Exception {
        runAndWait(GlobalExceptionHandlerCoverageTest::closeShowingDialogs);
        if (Files.isDirectory(LOG_FILE)) {
            Files.delete(LOG_FILE);
        }
        if (logExisted) {
            Files.writeString(LOG_FILE, originalLogContent);
        } else {
            Files.deleteIfExists(LOG_FILE);
        }
    }

    @Test
    void handleExceptionFromBackgroundThreadLogsDomainFailureAndShowsAlert() throws Exception {
        closeNextDialog();
        Thread thread = new Thread(
                () -> GlobalExceptionHandler.handleException(Thread.currentThread(), new InvalidPasswordException("bad password")),
                "handler-background-test"
        );

        thread.start();
        thread.join(5_000L);

        String log = waitForLog();
        assertTrue(log.contains("handler-background-test"));
        assertTrue(log.contains(InvalidPasswordException.class.getName()));
        assertTrue(log.contains("bad password"));
    }

    @Test
    void handleExceptionOnFxThreadLogsUnknownThreadAndShowsGenericAlert() throws Exception {
        closeNextDialog();

        runAndWait(() -> GlobalExceptionHandler.handleException(null, new RuntimeException("boom")));

        String log = waitForLog();
        assertTrue(log.contains("Unknown"));
        assertTrue(log.contains(RuntimeException.class.getName()));
        assertTrue(log.contains("boom"));
    }

    @Test
    void uncaughtExceptionDelegatesAndLoggingIOExceptionIsContained() throws Exception {
        closeNextDialog();
        runAndWait(() -> new GlobalExceptionHandler()
                .uncaughtException(Thread.currentThread(), new InsufficientBalanceException("low balance")));
        assertTrue(waitForLog().contains(InsufficientBalanceException.class.getName()));

        Files.deleteIfExists(LOG_FILE);
        Files.createDirectory(LOG_FILE);

        closeNextDialog();
        runAndWait(() -> GlobalExceptionHandler.handleException(
                Thread.currentThread(),
                new UserNotFound("missing user")
        ));

        assertTrue(Files.isDirectory(LOG_FILE));
    }

    private static String waitForLog() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (Files.exists(LOG_FILE)) {
                String log = Files.readString(LOG_FILE);
                if (!log.isBlank()) {
                    return log;
                }
            }
            Thread.sleep(25);
        }
        fail("Timed out waiting for exception log.");
        return "";
    }

    private static void startToolkit() {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
            if (!latch.await(10, TimeUnit.SECONDS)) {
                fail("Timed out while starting the JavaFX toolkit.");
            }
        } catch (IllegalStateException alreadyStarted) {
            Platform.setImplicitExit(false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while starting the JavaFX toolkit.");
        }
    }

    private static void runAndWait(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                fail("Timed out while waiting for JavaFX action.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while waiting for JavaFX action.");
        }

        Throwable throwable = failure.get();
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        if (throwable != null) {
            throw new AssertionError(throwable);
        }
    }

    private static void closeNextDialog() {
        AtomicBoolean handled = new AtomicBoolean(false);
        Thread responder = new Thread(() -> {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!handled.get() && System.nanoTime() < deadline) {
                CountDownLatch latch = new CountDownLatch(1);
                Platform.runLater(() -> {
                    try {
                        handled.set(closeShowingDialogs());
                    } finally {
                        latch.countDown();
                    }
                });
                try {
                    latch.await(250, TimeUnit.MILLISECONDS);
                    Thread.sleep(25);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "global-exception-dialog-responder");
        responder.setDaemon(true);
        responder.start();
    }

    private static boolean closeShowingDialogs() {
        boolean closed = false;
        for (Window window : List.copyOf(Window.getWindows())) {
            if (!window.isShowing() || window.getScene() == null) {
                continue;
            }
            DialogPane dialogPane = findDialogPane(window.getScene().getRoot());
            if (dialogPane == null) {
                continue;
            }
            Node button = dialogPane.lookupButton(ButtonType.OK);
            if (button instanceof ButtonBase buttonBase) {
                buttonBase.fire();
            } else {
                window.hide();
            }
            closed = true;
        }
        return closed;
    }

    private static DialogPane findDialogPane(Node node) {
        if (node instanceof DialogPane dialogPane) {
            return dialogPane;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                DialogPane dialogPane = findDialogPane(child);
                if (dialogPane != null) {
                    return dialogPane;
                }
            }
        }
        return null;
    }
}
