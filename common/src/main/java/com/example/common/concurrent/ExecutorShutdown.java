package com.example.common.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class ExecutorShutdown {
    private ExecutorShutdown() {
    }

    public static void shutdownGracefully(ExecutorService executor, long timeout, TimeUnit unit) {
        if (executor == null || executor.isShutdown()) {
            return;
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeout, unit)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
