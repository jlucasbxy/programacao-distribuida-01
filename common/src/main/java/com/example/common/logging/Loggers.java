package com.example.common.logging;

public final class Loggers {
    private Loggers() {}

    public static AppLogger console() {
        return new ConsoleLogger("");
    }

    public static AppLogger consoleWithPrefix(String prefix) {
        return new ConsoleLogger(prefix);
    }
}
