package com.example.common.logging;

public final class RawConsoleLogger implements AppLogger {
    private final String prefix;

    public RawConsoleLogger(String prefix) {
        this.prefix = prefix == null ? "" : prefix;
    }

    @Override
    public void info(String message) {
        writeLine(message);
    }

    @Override
    public void error(String message) {
        writeLine("ERROR: " + message);
    }

    private void writeLine(String message) {
        System.out.print(prefix + String.valueOf(message) + System.lineSeparator());
    }
}
