package com.example.common.logging;

import java.util.Locale;

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
        return create(settings);
    }

    public static AppLogger logger() {
        return withMode(DEFAULT_SCOPE, "", Output.LOGGER);
    }

    public static AppLogger logger(String scope) {
        return withMode(scope, "", Output.LOGGER);
    }

    public static AppLogger loggerWithPrefix(String scope, String prefix) {
        return withMode(scope, prefix, Output.LOGGER);
    }

    public static AppLogger disabled() {
        return withMode(DEFAULT_SCOPE, "", Output.DISABLED);
    }

    public static AppLogger withMode(String scope, String prefix, Output output) {
        return create(new Settings(scope, prefix, output));
    }

    public static AppLogger create(Settings settings) {
        return switch (settings.output()) {
            case LOGGER -> {
                String infoFileName = buildInfoFileName(settings.scope());
                String errorFileName = buildErrorFileName(settings.scope());
                yield new FileAndConsoleLogger(settings.prefix(), infoFileName, errorFileName);
            }
            case STDOUT -> new ConsoleLogger(settings.prefix());
            case DISABLED -> new NoOpLogger();
        };
    }

    public enum Output {
        LOGGER,
        STDOUT,
        DISABLED
    }

    public record Settings(String scope, String prefix, Output output) {
        public Settings(String scope, String prefix) {
            this(scope, prefix, Output.STDOUT);
        }

        public Settings {
            scope = normalizeScope(scope);
            prefix = prefix == null ? "" : prefix;
            output = output == null ? Output.STDOUT : output;
        }
    }

    private static String buildInfoFileName(String scope) {
        return normalizeFileScope(scope) + ".log";
    }

    private static String buildErrorFileName(String scope) {
        return normalizeFileScope(scope) + "-error.log";
    }

    private static String normalizeFileScope(String scope) {
        String normalized = normalizeScope(scope)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "-");
        return normalized.isBlank() ? DEFAULT_SCOPE : normalized;
    }

    private static String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return DEFAULT_SCOPE;
        }
        return scope.trim();
    }
}
