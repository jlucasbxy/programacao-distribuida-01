package com.example.common.logging;

public final class FileAndConsoleLogger implements AppLogger {
    @FunctionalInterface
    private interface LogAction {
        void log(AppLogger logger, String message);
    }

    private final AppLogger consoleLogger;
    private final AppLogger fileLogger;

    public FileAndConsoleLogger() {
        this("", new FileLogger());
    }

    public FileAndConsoleLogger(String consolePrefix) {
        this(consolePrefix, new FileLogger());
    }

    public FileAndConsoleLogger(String consolePrefix, String infoFileName) {
        this(consolePrefix, new FileLogger(infoFileName));
    }

    public FileAndConsoleLogger(String consolePrefix, String infoFileName, String errorFileName) {
        this(consolePrefix, new FileLogger(infoFileName, errorFileName));
    }

    private FileAndConsoleLogger(String consolePrefix, AppLogger fileLogger) {
        this.consoleLogger = new ConsoleLogger(consolePrefix);
        this.fileLogger = fileLogger;
    }

    @Override
    public void info(String message) {
        logWithFallback(message, AppLogger::info);
    }

    @Override
    public void error(String message) {
        logWithFallback(message, AppLogger::error);
    }

    private void logWithFallback(String message, LogAction action) {
        RuntimeException firstFailure = null;

        try {
            action.log(consoleLogger, message);
        } catch (RuntimeException e) {
            firstFailure = e;
        }

        try {
            action.log(fileLogger, message);
        } catch (RuntimeException e) {
            if (firstFailure != null) {
                e.addSuppressed(firstFailure);
            }
            throw e;
        }

        if (firstFailure != null) {
            throw firstFailure;
        }
    }
}
