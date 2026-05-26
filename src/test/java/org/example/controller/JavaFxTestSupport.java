package org.example.controller;

import javafx.application.Platform;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.fail;

final class JavaFxTestSupport {
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    private JavaFxTestSupport() {
    }

    static void startToolkit() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
            if (!latch.await(10, TimeUnit.SECONDS)) {
                fail("Timed out while starting the JavaFX toolkit.");
            }
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while starting the JavaFX toolkit.");
        }
    }
}
