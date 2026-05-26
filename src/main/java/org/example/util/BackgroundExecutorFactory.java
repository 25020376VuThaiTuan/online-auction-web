package org.example.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BackgroundExecutorFactory {
    private BackgroundExecutorFactory() {
    }

    public static ExecutorService newSingleThreadExecutor(String threadName) {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }
}
