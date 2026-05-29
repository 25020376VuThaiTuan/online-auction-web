package org.example.controller;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.PasswordField;
import javafx.stage.Window;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.fail;

final class JavaFxTestSupport {
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    private JavaFxTestSupport() {
    }

    static void startToolkit() {
        if (!STARTED.compareAndSet(false, true)) {
            Platform.setImplicitExit(false);
            return;
        }

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
            latch.countDown();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while starting the JavaFX toolkit.");
        }
    }

    static void runAndWait(Runnable action) {
        startToolkit();
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
                fail("Timed out while waiting for the JavaFX action.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while waiting for the JavaFX action.");
        }

        Throwable throwable = failure.get();
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        if (throwable != null) {
            throw new AssertionError("JavaFX action failed.", throwable);
        }
    }

    static void closeOpenDialogs() {
        startToolkit();
        runAndWait(() -> {
            for (Window window : java.util.List.copyOf(Window.getWindows())) {
                if (!window.isShowing() || window.getScene() == null) {
                    continue;
                }
                if (findDialogPane(window.getScene().getRoot()) != null) {
                    window.hide();
                }
            }
        });
    }

    static void closeNextDialog(ButtonType buttonType) {
        respondToNextDialog(null, false, buttonType);
    }

    static CompletableFuture<Boolean> closeNextDialogAndTrack(ButtonType buttonType) {
        return respondToNextDialog(null, false, buttonType);
    }

    static CompletableFuture<Boolean> closeNextDialogAndTrack(ButtonType buttonType, long timeoutMillis) {
        return respondToNextDialog(null, false, buttonType, null, timeoutMillis);
    }

    static void answerNextPasswordDialog(String password, boolean remember, ButtonType buttonType) {
        respondToNextDialog(password, remember, buttonType);
    }

    static void answerNextPasswordDialogThenCloseAlert(String password, boolean remember) {
        respondToNextDialog(password, remember, ButtonType.OK, () -> respondToNextDialog(null, false, ButtonType.OK));
    }

    static void closeNextDialogThenCloseAlert(ButtonType buttonType) {
        respondToNextDialog(null, false, buttonType, () -> respondToNextDialog(null, false, ButtonType.OK));
    }

    private static CompletableFuture<Boolean> respondToNextDialog(String password, boolean remember, ButtonType buttonType) {
        return respondToNextDialog(password, remember, buttonType, null);
    }

    private static CompletableFuture<Boolean> respondToNextDialog(
            String password,
            boolean remember,
            ButtonType buttonType,
            Runnable afterHandled
    ) {
        return respondToNextDialog(password, remember, buttonType, afterHandled, TimeUnit.SECONDS.toMillis(10));
    }

    private static CompletableFuture<Boolean> respondToNextDialog(
            String password,
            boolean remember,
            ButtonType buttonType,
            Runnable afterHandled,
            long timeoutMillis
    ) {
        startToolkit();
        AtomicBoolean handled = new AtomicBoolean(false);
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Thread responder = new Thread(() -> {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            while (!handled.get() && System.nanoTime() < deadline) {
                CountDownLatch latch = new CountDownLatch(1);
                Platform.runLater(() -> {
                    try {
                        if (tryRespondToOpenDialog(password, remember, buttonType, handled) && afterHandled != null) {
                            afterHandled.run();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
                try {
                    latch.await(250, TimeUnit.MILLISECONDS);
                    Thread.sleep(25);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    result.complete(false);
                    return;
                }
            }
            result.complete(handled.get());
        }, "javafx-dialog-responder");
        responder.setDaemon(true);
        responder.start();
        return result;
    }

    private static boolean tryRespondToOpenDialog(
            String password,
            boolean remember,
            ButtonType buttonType,
            AtomicBoolean handled
    ) {
        if (handled.get()) {
            return false;
        }
        for (Window window : Window.getWindows()) {
            if (!window.isShowing() || window.getScene() == null) {
                continue;
            }
            DialogPane dialogPane = findDialogPane(window.getScene().getRoot());
            if (dialogPane == null) {
                continue;
            }
            if (password != null) {
                setPasswordFields(dialogPane, password);
                setCheckBoxes(dialogPane, remember);
            }
            Node button = dialogPane.lookupButton(buttonType);
            handled.set(true);
            if (button instanceof ButtonBase buttonBase) {
                buttonBase.fire();
            } else {
                window.hide();
            }
            return true;
        }
        return false;
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

    private static void setPasswordFields(Node node, String password) {
        if (node instanceof PasswordField passwordField) {
            passwordField.setText(password);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                setPasswordFields(child, password);
            }
        }
    }

    private static void setCheckBoxes(Node node, boolean selected) {
        if (node instanceof CheckBox checkBox) {
            checkBox.setSelected(selected);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                setCheckBoxes(child, selected);
            }
        }
    }
}
