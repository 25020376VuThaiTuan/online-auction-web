package org.example;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ApplicationEntrypointCoverageTest {
    @BeforeAll
    static void initToolkit() {
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

    @Test
    void applicationStartMethodLoadsLoginScene() {
        runAndWait(() -> {
            Stage appStage = new Stage();
            new App().start(appStage);
            assertEquals("Online Auction System", appStage.getTitle());
            assertNotNull(appStage.getScene());
            appStage.close();
        });
    }

    @Test
    void launcherCanBeConstructed() {
        assertNotNull(new Launcher());
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
            assertTrue(latch.await(10, TimeUnit.SECONDS));
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
}
