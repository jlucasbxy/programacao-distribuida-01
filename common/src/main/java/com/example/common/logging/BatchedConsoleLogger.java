package com.example.common.logging;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class BatchedConsoleLogger implements AppLogger {
    private static final FileOutputStream STDOUT = new FileOutputStream(FileDescriptor.out);
    private static final FileOutputStream STDERR = new FileOutputStream(FileDescriptor.err);

    private static final int BATCH_SIZE = Integer.getInteger("logger.batch.size", 256);
    private static final long FLUSH_INTERVAL_MS = Long.getLong("logger.batch.flushMs", 50L);

    private static final Object LOCK = new Object();
    private static List<Entry> buffer = new ArrayList<>(BATCH_SIZE);
    private static volatile boolean shuttingDown = false;

    static {
        Thread flusher = new Thread(BatchedConsoleLogger::runFlusher, "batched-console-logger-flusher");
        flusher.setDaemon(true);
        flusher.start();
        Runtime.getRuntime().addShutdownHook(new Thread(BatchedConsoleLogger::shutdown, "batched-console-logger-shutdown"));
    }

    private final String prefix;

    public BatchedConsoleLogger(String prefix) {
        this.prefix = prefix == null ? "" : prefix;
    }

    @Override
    public void info(String message) {
        enqueue(STDOUT, message);
    }

    @Override
    public void error(String message) {
        enqueue(STDERR, message);
    }

    private void enqueue(FileOutputStream stream, String message) {
        String line = prefix + String.valueOf(message) + System.lineSeparator();
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        synchronized (LOCK) {
            buffer.add(new Entry(stream, bytes));
            if (buffer.size() >= BATCH_SIZE) {
                LOCK.notifyAll();
            }
        }
    }

    private static void runFlusher() {
        while (true) {
            List<Entry> batch;
            synchronized (LOCK) {
                long deadline = System.currentTimeMillis() + FLUSH_INTERVAL_MS;
                while (buffer.size() < BATCH_SIZE && !shuttingDown) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) break;
                    try {
                        LOCK.wait(remaining);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (buffer.isEmpty()) {
                    if (shuttingDown) return;
                    continue;
                }
                batch = buffer;
                buffer = new ArrayList<>(BATCH_SIZE);
            }
            writeBatch(batch);
        }
    }

    private static void writeBatch(List<Entry> batch) {
        FileOutputStream currentStream = null;
        ByteArrayOutputStream pending = new ByteArrayOutputStream(4096);
        try {
            for (Entry entry : batch) {
                if (currentStream != null && entry.stream != currentStream) {
                    currentStream.write(pending.toByteArray());
                    currentStream.flush();
                    pending.reset();
                }
                currentStream = entry.stream;
                pending.write(entry.bytes);
            }
            if (currentStream != null && pending.size() > 0) {
                currentStream.write(pending.toByteArray());
                currentStream.flush();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write batched log", e);
        }
    }

    private static void shutdown() {
        synchronized (LOCK) {
            shuttingDown = true;
            LOCK.notifyAll();
        }
        drain();
    }

    private static void drain() {
        while (true) {
            List<Entry> batch;
            synchronized (LOCK) {
                if (buffer.isEmpty()) return;
                batch = buffer;
                buffer = new ArrayList<>(BATCH_SIZE);
            }
            writeBatch(batch);
        }
    }

    private record Entry(FileOutputStream stream, byte[] bytes) {}
}
