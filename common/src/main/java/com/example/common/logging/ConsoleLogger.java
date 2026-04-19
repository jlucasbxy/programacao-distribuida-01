package com.example.common.logging;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class ConsoleLogger implements AppLogger {
    private static final Object OUTPUT_LOCK = new Object();
    private static final FileOutputStream STDOUT = new FileOutputStream(FileDescriptor.out);
    private static final FileOutputStream STDERR = new FileOutputStream(FileDescriptor.err);

    private final String prefix;

    public ConsoleLogger(String prefix) {
        this.prefix = prefix == null ? "" : prefix;
    }

    @Override
    public void info(String message) {
        writeLine(STDOUT, message);
    }

    @Override
    public void error(String message) {
        writeLine(STDERR, message);
    }

    private void writeLine(OutputStream outputStream, String message) {
        String line = prefix + String.valueOf(message) + System.lineSeparator();
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);

        synchronized (OUTPUT_LOCK) {
            try {
                outputStream.write(bytes);
                outputStream.flush();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to write log line", e);
            }
        }
    }
}
