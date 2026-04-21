package com.example.common.logging;

public final class Loggers {
    private static final String DEFAULT_SCOPE = "application";

    private Loggers() {}

    public static AppLogger console() {
        return console(new Settings(DEFAULT_SCOPE, ""));
    }

    public static AppLogger console(String scope) {
        return console(new Settings(scope, ""));
    }

    public static AppLogger consoleWithPrefix(String prefix) {
        return console(new Settings(DEFAULT_SCOPE, prefix));
    }

    public static AppLogger consoleWithPrefix(String scope, String prefix) {
        return console(new Settings(scope, prefix));
    }

    public static AppLogger console(Settings settings) {
        return new ConsoleLogger(settings.prefix());
    }

    public record Settings(String scope, String prefix) {
        public Settings {
            scope = normalizeScope(scope);
            prefix = prefix == null ? "" : prefix;
        }
    }

    private static String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return DEFAULT_SCOPE;
        }
        return scope.trim();
    }
}
